#!/usr/bin/env swift
import AppKit
import Foundation

/// 将方形图缩放后居中放入 size×size 画布（透明边），避免桌面圆形/圆角裁切时切掉四角。
func main() {
    let args = CommandLine.arguments
    guard args.count >= 4 else {
        fputs("Usage: pad_icon_square.swift <input.png> <output.png> <scale 0..1>\n", stderr)
        exit(1)
    }
    let input = args[1]
    let output = args[2]
    guard let scale = Double(args[3]), scale > 0, scale <= 1 else {
        fputs("Invalid scale\n", stderr)
        exit(2)
    }

    guard let src = NSImage(contentsOfFile: input) else {
        fputs("Cannot load \(input)\n", stderr)
        exit(3)
    }

    let side: CGFloat = 512
    let canvas = NSImage(size: NSSize(width: side, height: side))
    canvas.lockFocus()
    NSColor.clear.set()
    NSBezierPath(rect: NSRect(x: 0, y: 0, width: side, height: side)).fill()

    let drawSide = side * CGFloat(scale)
    let ox = (side - drawSide) / 2
    let oy = (side - drawSide) / 2
    let dst = NSRect(x: ox, y: oy, width: drawSide, height: drawSide)
    let srcSize = src.size
    let from = NSRect(origin: .zero, size: srcSize)
    src.draw(in: dst, from: from, operation: .sourceOver, fraction: 1.0)
    canvas.unlockFocus()

    guard let tiff = canvas.tiffRepresentation,
          let rep = NSBitmapImageRep(data: tiff) else {
        fputs("Bitmap rep failed\n", stderr)
        exit(4)
    }
    let props: [NSBitmapImageRep.PropertyKey: Any] = [.compressionFactor: 1.0]
    guard let data = rep.representation(using: .png, properties: props) else {
        fputs("PNG encode failed\n", stderr)
        exit(5)
    }
    do {
        try data.write(to: URL(fileURLWithPath: output))
    } catch {
        fputs("Write failed: \(error)\n", stderr)
        exit(6)
    }
}

main()
