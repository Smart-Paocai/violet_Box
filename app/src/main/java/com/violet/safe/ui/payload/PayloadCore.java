package com.violet.safe.ui.payload;

import android.os.Build;
import android.text.TextUtils;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class PayloadCore {

    private PayloadCore() {
    }

    static final class PayloadSession implements Closeable {
        final String fileName;
        final Manifest manifest;
        final long dataOffset;
        final int blockSize;
        final long archiveSize;
        final boolean local;
        final RandomAccessFile localRaf;
        final HttpReader httpReader;

        PayloadSession(String fileName, Manifest manifest, long dataOffset,
                       int blockSize, long archiveSize, RandomAccessFile localRaf) {
            this.fileName = fileName;
            this.manifest = manifest;
            this.dataOffset = dataOffset;
            this.blockSize = blockSize;
            this.archiveSize = archiveSize;
            this.local = true;
            this.localRaf = localRaf;
            this.httpReader = null;
        }

        PayloadSession(String fileName, Manifest manifest, long dataOffset,
                       int blockSize, long archiveSize, HttpReader httpReader) {
            this.fileName = fileName;
            this.manifest = manifest;
            this.dataOffset = dataOffset;
            this.blockSize = blockSize;
            this.archiveSize = archiveSize;
            this.local = false;
            this.localRaf = null;
            this.httpReader = httpReader;
        }

        @Override
        public void close() throws IOException {
            if (localRaf != null) {
                localRaf.close();
            }
            if (httpReader != null) {
                httpReader.close();
            }
        }

        String getSecurityPatchLevel() {
            return manifest.securityPatchLevel;
        }
    }

    static final class PartitionInfoRow {
        final String name;
        final long size;
        final long rawSize;
        final String sha256;

        PartitionInfoRow(String name, long size, long rawSize, String sha256) {
            this.name = name;
            this.size = size;
            this.rawSize = rawSize;
            this.sha256 = sha256;
        }
    }

    static List<PartitionInfoRow> getPartitionInfoList(PayloadSession session) {
        List<PartitionInfoRow> rows = new ArrayList<>();
        for (Partition it : session.manifest.partitions) {
            if (it.operations.isEmpty()) {
                continue;
            }
            Operation first = it.operations.get(0);
            Operation last = it.operations.get(it.operations.size() - 1);
            long raw = (last.dataOffset + last.dataLength) - first.dataOffset;
            rows.add(new PartitionInfoRow(
                    it.name,
                    it.newSize,
                    raw,
                    toHex(it.newHash)
            ));
        }
        return rows;
    }

    static boolean isValidUrl(String value) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && !TextUtils.isEmpty(host);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    static PayloadSession parseFromPath(String path) throws IOException {
        long payloadOffset = getPayloadOffset(path, null);
        if (payloadOffset < 0) {
            throw new IOException("无法定位 payload.bin");
        }
        RandomAccessFile raf = new RandomAccessFile(path, "r");
        try {
            return initPayloadLocal(new File(path).getName(), raf, payloadOffset);
        } catch (IOException e) {
            raf.close();
            throw e;
        }
    }

    static PayloadSession parseFromUrl(String url, String userAgent) throws IOException {
        HttpReader http = new HttpReader();
        http.init(url, userAgent);
        long payloadOffset = getPayloadOffset(url, http);
        if (payloadOffset < 0) {
            http.close();
            throw new IOException("无法定位远程 payload.bin");
        }
        try {
            return initPayloadHttp(http.getFileName(), http, payloadOffset);
        } catch (IOException e) {
            http.close();
            throw e;
        }
    }

    static void extractPartition(PayloadSession session, String partitionName, String outputDir,
                                 ProgressCallback callback) throws IOException {
        Partition target = null;
        for (Partition p : session.manifest.partitions) {
            if (partitionName.equals(p.name)) {
                target = p;
                break;
            }
        }
        if (target == null) {
            throw new IOException("未找到分区: " + partitionName);
        }
        File dir = new File(outputDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建目录: " + outputDir);
        }
        File outFile = new File(dir, partitionName + ".img");
        try (RandomAccessFile partOut = new RandomAccessFile(outFile, "rw")) {
            for (Operation op : target.operations) {
                if (session.local) {
                    extractFromLocal(op, partOut, session.localRaf, session.blockSize, session.dataOffset);
                } else {
                    extractFromHttp(op, partOut, session.httpReader, session.blockSize, session.dataOffset);
                }
                if (callback != null) {
                    callback.onProgress(partOut.getFilePointer(), outFile.getAbsolutePath());
                }
            }
        }
    }

    private static PayloadSession initPayloadLocal(String fileName, RandomAccessFile raf, long payloadOffset) throws IOException {
        raf.seek(payloadOffset);
        byte[] magic = new byte[4];
        raf.readFully(magic);
        if (!"CrAU".equals(new String(magic, StandardCharsets.UTF_8))) {
            throw new IOException("Invalid magic");
        }
        long fileFormatVersion = raf.readLong();
        if (fileFormatVersion != 2L) {
            throw new IOException("Unsupported version: " + fileFormatVersion);
        }
        long manifestSize = raf.readLong();
        int metadataSignatureSize = raf.readInt();
        byte[] manifestBytes = new byte[(int) manifestSize];
        raf.readFully(manifestBytes);
        if (metadataSignatureSize > 0) {
            raf.skipBytes(metadataSignatureSize);
        }
        Manifest dm = new ManifestParser(manifestBytes).parse();
        return new PayloadSession(fileName, dm, raf.getFilePointer(), dm.blockSize, raf.length(), raf);
    }

    private static PayloadSession initPayloadHttp(String fileName, HttpReader http, long payloadOffset) throws IOException {
        http.seek(payloadOffset);
        byte[] magic = new byte[4];
        http.readFully(magic);
        if (!"CrAU".equals(new String(magic, StandardCharsets.UTF_8))) {
            throw new IOException("Invalid magic");
        }
        byte[] versionBytes = new byte[8];
        http.readFully(versionBytes);
        long fileFormatVersion = toLong(versionBytes);
        if (fileFormatVersion != 2L) {
            throw new IOException("Unsupported version: " + fileFormatVersion);
        }
        byte[] manifestSizeBytes = new byte[8];
        http.readFully(manifestSizeBytes);
        long manifestSize = toLong(manifestSizeBytes);
        byte[] sigBytes = new byte[4];
        http.readFully(sigBytes);
        int metadataSignatureSize = (int) toLong(sigBytes);
        byte[] manifestBytes = new byte[(int) manifestSize];
        http.readFully(manifestBytes);
        if (metadataSignatureSize > 0) {
            byte[] skip = new byte[metadataSignatureSize];
            http.readFully(skip);
        }
        Manifest dm = new ManifestParser(manifestBytes).parse();
        return new PayloadSession(fileName, dm, http.position(), dm.blockSize, http.length(), http);
    }

    private static long getPayloadOffset(String pathOrUrl, HttpReader http) throws IOException {
        if (pathOrUrl.endsWith(".bin")) {
            return 0;
        }
        byte[] endBytes = new byte[4096];
        final String payloadFile = "payload.bin";
        if (isValidUrl(pathOrUrl)) {
            if (http == null) throw new IOException("http reader not initialized");
            http.seek(http.length() - 4096);
            http.readFully(endBytes);
            FileInfo cdInfo = locateCentralDirectory(endBytes, http.length());
            http.seek(cdInfo.offset);
            byte[] central = new byte[(int) cdInfo.size];
            http.readFully(central);
            long headerOffset = locateLocalFileHeader(central, payloadFile);
            byte[] localHeader = new byte[256];
            http.seek(headerOffset);
            http.readFully(localHeader);
            return locateLocalFileOffset(localHeader) + headerOffset;
        } else {
            try (RandomAccessFile raf = new RandomAccessFile(pathOrUrl, "r")) {
                raf.seek(raf.length() - 4096);
                raf.readFully(endBytes);
                FileInfo cdInfo = locateCentralDirectory(endBytes, raf.length());
                raf.seek(cdInfo.offset);
                byte[] central = new byte[(int) cdInfo.size];
                raf.readFully(central);
                long headerOffset = locateLocalFileHeader(central, payloadFile);
                byte[] localHeader = new byte[256];
                raf.seek(headerOffset);
                raf.readFully(localHeader);
                return locateLocalFileOffset(localHeader) + headerOffset;
            }
        }
    }

    private static void extractFromLocal(Operation op, RandomAccessFile partOutput,
                                         RandomAccessFile raf, int blockSize, long offset) throws IOException {
        raf.seek(offset + op.dataOffset);
        switch (op.type) {
            case 8:
                IOUtils.copy(new XZCompressorInputStream(new RafSliceInputStream(raf, op.dataLength)), new ExtentOutputStream(partOutput, op.dstExtents, blockSize));
                break;
            case 1:
                IOUtils.copy(new BZip2CompressorInputStream(new BufferedInputStream(new RafSliceInputStream(raf, op.dataLength))),
                        new ExtentOutputStream(partOutput, op.dstExtents, blockSize));
                break;
            case 0:
                byte[] data = new byte[(int) op.dataLength];
                raf.readFully(data);
                try (ExtentOutputStream out = new ExtentOutputStream(partOutput, op.dstExtents, blockSize)) {
                    out.write(data);
                }
                break;
            case 6:
                try (ExtentOutputStream out = new ExtentOutputStream(partOutput, op.dstExtents, blockSize)) {
                    out.write(new byte[(int) op.dataLength]);
                }
                break;
            default:
                throw new IOException("Unsupported op: " + op.type);
        }
    }

    private static void extractFromHttp(Operation op, RandomAccessFile partOutput,
                                        HttpReader http, int blockSize, long offset) throws IOException {
        switch (op.type) {
            case 8: {
                try (InputStream compressed = http.openRangeStream(offset + op.dataOffset, op.dataLength);
                     XZCompressorInputStream xzIn = new XZCompressorInputStream(new BufferedInputStream(compressed));
                     ExtentOutputStream out = new ExtentOutputStream(partOutput, op.dstExtents, blockSize)) {
                    IOUtils.copy(xzIn, out);
                }
                break;
            }
            case 1: {
                try (InputStream compressed = http.openRangeStream(offset + op.dataOffset, op.dataLength);
                     BZip2CompressorInputStream bzIn = new BZip2CompressorInputStream(new BufferedInputStream(compressed));
                     ExtentOutputStream out = new ExtentOutputStream(partOutput, op.dstExtents, blockSize)) {
                    IOUtils.copy(bzIn, out);
                }
                break;
            }
            case 0: {
                try (InputStream raw = http.openRangeStream(offset + op.dataOffset, op.dataLength);
                     ExtentOutputStream out = new ExtentOutputStream(partOutput, op.dstExtents, blockSize)) {
                    IOUtils.copy(raw, out);
                }
                break;
            }
            case 6:
                try (ExtentOutputStream out = new ExtentOutputStream(partOutput, op.dstExtents, blockSize)) {
                    out.write(new byte[(int) op.dataLength]);
                }
                break;
            default:
                throw new IOException("Unsupported op: " + op.type);
        }
    }

    interface ProgressCallback {
        void onProgress(long bytes, String outPath);
    }

    private static long toLong(byte[] bytes) {
        long result = 0L;
        for (byte b : bytes) {
            result = (result << 8) | (b & 0xffL);
        }
        return result;
    }

    private static String toHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static final class FileInfo {
        final long offset;
        final long size;

        FileInfo(long offset, long size) {
            this.offset = offset;
            this.size = size;
        }
    }

    private static FileInfo locateCentralDirectory(byte[] bytes, long fileLength) throws IOException {
        final long ENDSIG = 0x06054b50L;
        final long ZIP64_ENDSIG = 0x06064b50L;
        final long ZIP64_LOCSIG = 0x07064b50L;
        final int ENDHDR = 22;
        final int ZIP64_LOCHDR = 20;
        final long ZIP64_MAGICVAL = 0xFFFFFFFFL;

        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int offset = bb.capacity() - ENDHDR;
        long cenSize = -1;
        long cenOffset = -1;

        for (int i = 0; i <= bb.capacity() - ENDHDR; i++) {
            bb.position(offset - i);
            if ((bb.getInt() & 0xffffffffL) == ENDSIG) {
                int endSigOffset = bb.position();
                bb.position(bb.position() + 12);
                if ((bb.getInt() & 0xffffffffL) == ZIP64_MAGICVAL) {
                    bb.position(endSigOffset - ZIP64_LOCHDR - 4);
                    if ((bb.getInt() & 0xffffffffL) == ZIP64_LOCSIG) {
                        bb.position(bb.position() + 4);
                        long zip64EndSigOffset = bb.getLong();
                        bb.position((int) (4096 - (fileLength - zip64EndSigOffset)));
                        if ((bb.getInt() & 0xffffffffL) == ZIP64_ENDSIG) {
                            bb.position(bb.position() + 36);
                            cenSize = bb.getLong();
                            cenOffset = bb.getLong();
                        }
                    }
                } else {
                    bb.position(endSigOffset + 8);
                    cenSize = bb.getInt() & 0xffffffffL;
                    cenOffset = bb.getInt() & 0xffffffffL;
                    break;
                }
            }
        }
        if (cenOffset < 0 || cenSize <= 0) {
            throw new IOException("failed to locate central directory");
        }
        return new FileInfo(cenOffset, cenSize);
    }

    private static long locateLocalFileHeader(byte[] centralDirectoryBytes, String fileName) {
        final long CENSIG = 0x02014b50L;
        ByteBuffer bb = ByteBuffer.wrap(centralDirectoryBytes).order(ByteOrder.LITTLE_ENDIAN);
        long localHeaderOffset = -1;
        while (bb.remaining() >= 4) {
            if ((bb.getInt() & 0xffffffffL) == CENSIG) {
                bb.position(bb.position() + 24);
                int fileNameLen = bb.getShort() & 0xffff;
                int extraLen = bb.getShort() & 0xffff;
                int commentLen = bb.getShort() & 0xffff;
                bb.position(bb.position() + 8);
                long offset = bb.getInt() & 0xffffffffL;
                byte[] nameBytes = new byte[fileNameLen];
                bb.get(nameBytes);
                if (fileName.equals(new String(nameBytes, StandardCharsets.UTF_8))) {
                    localHeaderOffset = offset;
                    break;
                }
                bb.position(bb.position() + extraLen + commentLen);
            } else {
                break;
            }
        }
        return localHeaderOffset;
    }

    private static long locateLocalFileOffset(byte[] localHeaderBytes) throws IOException {
        final long LOCSIG = 0x04034b50L;
        ByteBuffer bb = ByteBuffer.wrap(localHeaderBytes).order(ByteOrder.LITTLE_ENDIAN);
        if ((bb.getInt() & 0xffffffffL) != LOCSIG) {
            throw new IOException("invalid local header");
        }
        bb.position(bb.position() + 22);
        int fileNameLen = bb.getShort() & 0xffff;
        int extraLen = bb.getShort() & 0xffff;
        bb.position(bb.position() + fileNameLen + extraLen);
        return bb.position();
    }

    static final class HttpReader implements Closeable {
        private final OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        private static final String DEFAULT_UA = buildDefaultUserAgent();
        private String url;
        private String fileName;
        private long fileLength;
        private long position;
        private String userAgent = DEFAULT_UA;

        private static String buildDefaultUserAgent() {
            StringBuilder sb = new StringBuilder();
            String release = Build.VERSION.RELEASE_OR_CODENAME;
            String buildId = Build.ID;
            boolean validRelease = !TextUtils.isEmpty(release);
            boolean validId = !TextUtils.isEmpty(buildId);
            boolean includeModel = "REL".equals(Build.VERSION.CODENAME) && !TextUtils.isEmpty(Build.MODEL);

            sb.append("AndroidDownloadManager");
            if (validRelease) {
                sb.append("/").append(release);
            }

            sb.append(" (Linux; U; Android");
            if (validRelease) {
                sb.append(" ").append(release);
            }

            if (includeModel || validId) {
                sb.append(";");
                if (includeModel) {
                    sb.append(" ").append(Build.MODEL);
                }
                if (validId) {
                    sb.append(" Build/").append(buildId);
                }
            }
            sb.append(")");
            return sb.toString();
        }

        void init(String url, String userAgent) throws IOException {
            this.url = url;
            String ua = TextUtils.isEmpty(userAgent) ? DEFAULT_UA : userAgent;
            this.userAgent = ua;

            // Prefer a 1-byte ranged request to avoid downloading the full file.
            // Some hosts reject Range probes; fall back to a normal request for headers only.
            Response response = null;
            try {
                response = client.newCall(new Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", ua)
                        .addHeader("Accept", "*/*")
                        .addHeader("Range", "bytes=0-0")
                        .build()).execute();

                if (!response.isSuccessful()) {
                    int code = response.code();
                    response.close();
                    response = client.newCall(new Request.Builder()
                            .url(url)
                            .addHeader("User-Agent", ua)
                            .addHeader("Accept", "*/*")
                            .build()).execute();
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP init failed: " + response.code() + " " + response.message());
                    }
                    String cl = response.header("Content-Length");
                    fileLength = TextUtils.isEmpty(cl) ? response.body() != null ? response.body().contentLength() : 0L : Long.parseLong(cl);
                } else {
                    String cr = response.header("Content-Range");
                    if (cr != null && cr.contains("/")) {
                        fileLength = Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1));
                    } else {
                        String cl = response.header("Content-Length");
                        fileLength = TextUtils.isEmpty(cl) ? response.body() != null ? response.body().contentLength() : 0L : Long.parseLong(cl);
                    }
                }

                fileName = getFileNameFromHeaders(response.headers());
                position = 0L;
            } finally {
                if (response != null) response.close();
            }
        }

        long length() {
            return fileLength;
        }

        long position() {
            return position;
        }

        String getFileName() {
            return fileName;
        }

        void seek(long bytePosition) {
            if (bytePosition < 0 || bytePosition >= fileLength) {
                throw new IllegalArgumentException("invalid seek: " + bytePosition);
            }
            position = bytePosition;
        }

        void readFully(byte[] target) throws IOException {
            String range = "bytes=" + position + "-" + (position + target.length - 1);
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", userAgent)
                    .addHeader("Accept", "*/*")
                    .addHeader("Range", range)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected response: " + response.code());
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("empty response body");
                }
                InputStream in = body.byteStream();
                int readTotal = 0;
                while (readTotal < target.length) {
                    int r = in.read(target, readTotal, target.length - readTotal);
                    if (r < 0) {
                        throw new IOException("unexpected EOF in ranged read");
                    }
                    readTotal += r;
                }
                position += readTotal;
            }
        }

        InputStream openRangeStream(long start, long length) throws IOException {
            if (length < 0) {
                throw new IOException("invalid length: " + length);
            }
            if (length == 0) {
                return new java.io.ByteArrayInputStream(new byte[0]);
            }
            long end = start + length - 1;
            String range = "bytes=" + start + "-" + end;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", userAgent)
                    .addHeader("Accept", "*/*")
                    .addHeader("Range", range)
                    .build();
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                response.close();
                throw new IOException("Unexpected response: " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                response.close();
                throw new IOException("empty response body");
            }
            return new BoundedResponseInputStream(body.byteStream(), response, length);
        }

        private String getFileNameFromHeaders(Headers headers) {
            String contentDisposition = headers.get("Content-Disposition");
            if (!TextUtils.isEmpty(contentDisposition)) {
                String[] parts = contentDisposition.split(";");
                for (String part : parts) {
                    String p = part.trim();
                    if (p.startsWith("filename=")) {
                        return p.substring("filename=".length()).replace("\"", "");
                    }
                }
            }
            try {
                return new File(URI.create(url).getPath()).getName();
            } catch (Exception e) {
                return "remote_payload.zip";
            }
        }

        @Override
        public void close() {
        }
    }

    private static final class Manifest {
        int blockSize = 4096;
        String securityPatchLevel = "";
        final List<Partition> partitions = new ArrayList<>();
    }

    private static final class Partition {
        String name = "";
        long newSize = 0;
        byte[] newHash = new byte[0];
        final List<Operation> operations = new ArrayList<>();
    }

    private static final class Operation {
        int type;
        long dataOffset;
        long dataLength;
        final List<Extent> dstExtents = new ArrayList<>();
    }

    private static final class Extent {
        long startBlock;
        long numBlocks;
    }

    private static final class BoundedResponseInputStream extends InputStream {
        private final InputStream delegate;
        private final Response response;
        private long remaining;
        private boolean closed;

        BoundedResponseInputStream(InputStream delegate, Response response, long remaining) {
            this.delegate = delegate;
            this.response = response;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = delegate.read();
            if (b < 0) {
                throw new IOException("unexpected EOF in ranged stream");
            }
            remaining--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int read = delegate.read(b, off, toRead);
            if (read < 0) {
                throw new IOException("unexpected EOF in ranged stream");
            }
            remaining -= read;
            return read;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                delegate.close();
            } finally {
                response.close();
            }
        }
    }

    private static final class ManifestParser {
        private final ProtoReader root;

        ManifestParser(byte[] bytes) {
            this.root = new ProtoReader(bytes);
        }

        Manifest parse() throws IOException {
            Manifest m = new Manifest();
            while (root.hasNext()) {
                int tag = root.readTag();
                int field = tag >>> 3;
                int wire = tag & 0x7;
                if (field == 3 && wire == 0) {
                    m.blockSize = (int) root.readVarint64();
                } else if (field == 13 && wire == 2) {
                    m.partitions.add(parsePartition(new ProtoReader(root.readLengthDelimited())));
                } else if (field == 18 && wire == 2) {
                    m.securityPatchLevel = root.readString();
                } else {
                    root.skipField(wire);
                }
            }
            return m;
        }

        private Partition parsePartition(ProtoReader r) throws IOException {
            Partition p = new Partition();
            while (r.hasNext()) {
                int tag = r.readTag();
                int field = tag >>> 3;
                int wire = tag & 0x7;
                if (field == 1 && wire == 2) {
                    p.name = r.readString();
                } else if (field == 7 && wire == 2) {
                    parsePartitionInfo(new ProtoReader(r.readLengthDelimited()), p);
                } else if (field == 8 && wire == 2) {
                    p.operations.add(parseOperation(new ProtoReader(r.readLengthDelimited())));
                } else {
                    r.skipField(wire);
                }
            }
            return p;
        }

        private void parsePartitionInfo(ProtoReader r, Partition p) throws IOException {
            while (r.hasNext()) {
                int tag = r.readTag();
                int field = tag >>> 3;
                int wire = tag & 0x7;
                if (field == 1 && wire == 0) {
                    p.newSize = r.readVarint64();
                } else if (field == 2 && wire == 2) {
                    p.newHash = r.readLengthDelimited();
                } else {
                    r.skipField(wire);
                }
            }
        }

        private Operation parseOperation(ProtoReader r) throws IOException {
            Operation op = new Operation();
            while (r.hasNext()) {
                int tag = r.readTag();
                int field = tag >>> 3;
                int wire = tag & 0x7;
                if (field == 1 && wire == 0) {
                    op.type = (int) r.readVarint64();
                } else if (field == 2 && wire == 0) {
                    op.dataOffset = r.readVarint64();
                } else if (field == 3 && wire == 0) {
                    op.dataLength = r.readVarint64();
                } else if (field == 6 && wire == 2) {
                    op.dstExtents.add(parseExtent(new ProtoReader(r.readLengthDelimited())));
                } else {
                    r.skipField(wire);
                }
            }
            return op;
        }

        private Extent parseExtent(ProtoReader r) throws IOException {
            Extent e = new Extent();
            while (r.hasNext()) {
                int tag = r.readTag();
                int field = tag >>> 3;
                int wire = tag & 0x7;
                if (field == 1 && wire == 0) {
                    e.startBlock = r.readVarint64();
                } else if (field == 2 && wire == 0) {
                    e.numBlocks = r.readVarint64();
                } else {
                    r.skipField(wire);
                }
            }
            return e;
        }
    }

    private static final class ProtoReader {
        private final byte[] data;
        private int pos = 0;

        ProtoReader(byte[] data) {
            this.data = data;
        }

        boolean hasNext() {
            return pos < data.length;
        }

        int readTag() throws IOException {
            if (!hasNext()) throw new IOException("eof tag");
            return (int) readVarint64();
        }

        long readVarint64() throws IOException {
            long result = 0;
            int shift = 0;
            while (shift < 64) {
                if (pos >= data.length) throw new IOException("eof varint");
                int b = data[pos++] & 0xff;
                result |= (long) (b & 0x7f) << shift;
                if ((b & 0x80) == 0) return result;
                shift += 7;
            }
            throw new IOException("varint too long");
        }

        byte[] readLengthDelimited() throws IOException {
            long len = readVarint64();
            if (len < 0 || pos + len > data.length) throw new IOException("invalid len");
            byte[] out = new byte[(int) len];
            System.arraycopy(data, pos, out, 0, (int) len);
            pos += (int) len;
            return out;
        }

        String readString() throws IOException {
            return new String(readLengthDelimited(), StandardCharsets.UTF_8);
        }

        void skipField(int wireType) throws IOException {
            switch (wireType) {
                case 0:
                    readVarint64();
                    return;
                case 1:
                    skip(8);
                    return;
                case 2:
                    skip((int) readVarint64());
                    return;
                case 5:
                    skip(4);
                    return;
                default:
                    throw new IOException("wire=" + wireType);
            }
        }

        private void skip(int n) throws IOException {
            if (n < 0 || pos + n > data.length) throw new IOException("skip out");
            pos += n;
        }
    }

    private static final class RafSliceInputStream extends InputStream {
        private final RandomAccessFile raf;
        private long remaining;

        RafSliceInputStream(RandomAccessFile raf, long remaining) {
            this.raf = raf;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int v = raf.read();
            if (v >= 0) remaining--;
            return v;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int toRead = (int) Math.min(len, remaining);
            int r = raf.read(b, off, toRead);
            if (r > 0) remaining -= r;
            return r;
        }
    }

    private static final class ExtentOutputStream extends java.io.OutputStream {
        private final RandomAccessFile raf;
        private final List<Extent> extents;
        private final int blockSize;
        private int idx = 0;
        private long offsetInExtent = 0;

        ExtentOutputStream(RandomAccessFile raf, List<Extent> extents, int blockSize) throws IOException {
            this.raf = raf;
            this.extents = extents;
            this.blockSize = blockSize;
            seekCurrent();
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            int remain = len;
            int cursor = off;
            while (remain > 0) {
                if (idx >= extents.size()) {
                    throw new IOException("write exceeds extents");
                }
                Extent e = extents.get(idx);
                long avail = e.numBlocks * blockSize - offsetInExtent;
                if (avail <= 0) {
                    idx++;
                    offsetInExtent = 0;
                    seekCurrent();
                    continue;
                }
                int w = (int) Math.min(remain, avail);
                raf.write(b, cursor, w);
                cursor += w;
                remain -= w;
                offsetInExtent += w;
            }
        }

        private void seekCurrent() throws IOException {
            if (idx >= extents.size()) return;
            Extent e = extents.get(idx);
            raf.seek(e.startBlock * blockSize + offsetInExtent);
        }
    }
}
