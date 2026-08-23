# Rainbow Indent Guide

A fork of [Indent Guide](https://github.com/kiritsuku/IndentGuide) for Eclipse,
bringing the guides closer to what IntelliJ IDEA draws.

- one color per indentation level (rainbow), cycled past the seventh
- the guide of the block holding the caret is lightened, and follows the caret
- guides follow the brace structure: they start below the line opening the
  block and reach the brace closing it
- blank lines and comments no longer break the vertical lines
- sub-pixel guide placement, so the lines do not drift right on deep levels
  when DPI scaling is on
- optional greying of the single guide that does not line up with the brace
  opening or closing its block
- the common content types (Java, XML, properties, JSP, HTML, CSS, JS, PHP,
  C/C++) are enabled out of the box

## Layout

    alien.rainbow.indentguide/            plug-in
    alien.rainbow.indentguide.feature/    feature
    alien.rainbow.indentguide.updateSite/ generated p2 repository

`RainbowIndentGuide-1.0.0-updatesite.zip` is the p2 repository, installable via
*Help > Install New Software... > Add... > Archive...*

## Requirements

Eclipse 3.6 or later, Java 8 or later. The plug-in is compiled with
`javac --release 8`, so an API newer than Java 8 fails at compile time rather
than throwing `NoSuchMethodError` at runtime.

## License

This repository is a fork. The original work stays under the MIT License,
Copyright (c) 2014 Simon Schaefer; parts of the painter derive from the Eclipse
Platform and stay under the Eclipse Public License v1.0, Copyright (c) 2006,
2009 Wind River Systems, Inc., IBM Corporation and others. The modifications and
new files contributed by this fork are Copyright (c) 2026 Simone Platania and
are released under the Eclipse Public License v1.0.

The notices in the individual source files are authoritative. See `LICENSE`.
