#!/usr/bin/env swift
import AppKit
import Foundation

/// 从正方形图源取「右上对齐」的正方形子区域：裁掉左下，保留右上。
/// 子区域边长 = floor(min(w,h) * ratio)，默认 ratio=0.82（略放大人物、去掉左下角留白）。
func main() {
    let args = CommandLine.arguments
    guard args.count >= 3 else {
        fputs("Usage: crop_icon_top_right.swift <input.png> <output.png> [ratio 0..1]\n", stderr)
        exit(1)
    }
    let inputPath = args[1]
    let outputPath = args[2]
    let ratio: CGFloat
    if args.count >= 4, let r = Double(args[3]), r > 0, r <= 1 {
        ratio = CGFloat(r)
    } else {
        ratio = 0.82
    }

    guard let img = NSImage(contentsOfFile: inputPath) else {
        fputs("Failed to load image: \(inputPath)\n", stderr)
        exit(2)
    }
    guard let tiff = img.tiffRepresentation,
          let rep = NSBitmapImageRep(data: tiff) else {
        fputs("Failed to get bitmap representation\n", stderr)
        exit(3)
    }

    let w = rep.pixelsWide
    let h = rep.pixelsHigh
    let side = Int(CGFloat(min(w, h)) * ratio)
    guard side > 0 else {
        fputs("Invalid side\n", stderr)
        exit(4)
    }

    // NSBitmapImageRep: (0,0) 为左下角；要「保留右上」= 取 x 最大、y 最大 的方形区域
    let left = w - side
    let bottom = h - side

    let rect = NSRect(x: left, y: bottom, width: side, height: side)
    guard let cg = rep.cgImage else {
        fputs("No CGImage\n", stderr)
        exit(5)
    }
    guard let cropped = cg.cropping(to: rect) else {
        fputs("Crop failed\n", stderr)
        exit(6)
    }

    let out = NSImage(cgImage: cropped, size: NSSize(width: side, height: side))
    guard let outTiff = out.tiffRepresentation,
          let outRep = NSBitmapImageRep(data: outTiff) else {
        fputs("Failed to build output bitmap\n", stderr)
        exit(7)
    }
    let props: [NSBitmapImageRep.PropertyKey: Any] = [.compressionFactor: 1.0]
    guard let pngData = outRep.representation(using: .png, properties: props) else {
        fputs("Failed to encode PNG\n", stderr)
        exit(8)
    }
    do {
        try pngData.write(to: URL(fileURLWithPath: outputPath))
    } catch {
        fputs("Write failed: \(error)\n", stderr)
        exit(9)
    }
}

main()
