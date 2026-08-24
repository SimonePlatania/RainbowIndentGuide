# Rainbow Indent Guide

🌈Multi-level rainbow indent guides for Eclipse — a fork of [Indent Guide](https://github.com/kiritsuku/IndentGuide), rebuilt to work on current releases.🌈

**[⬇ Download 1.0.0](https://github.com/SimonePlatania/RainbowIndentGuide/releases/tag/v1.0.0)**

<img width="1536" height="1024" alt="Rainbow indent guides in the Eclipse Java editor" src="https://github.com/user-attachments/assets/800262c6-f1c7-4642-af5b-149a587b743a" />

## Features

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

Everything is configurable under
*Window > Preferences > General > Editors > Text Editors > Indent Guide*,
with the colors on the **Style** subpage. Changes apply immediately, without
reopening editors.

## Installation

Download `RainbowIndentGuide-1.0.0-updatesite.zip` from the
[latest release](https://github.com/SimonePlatania/RainbowIndentGuide/releases/latest).
Don't unzip it.

1. *Help > Install New Software...*
2. *Add... > Archive...* and pick the zip
3. tick **Rainbow Indent Guide**, then Next, accept the license, Finish
4. restart when prompted

Eclipse will warn about unsigned content — the jar has no digital signature.
Confirm and continue.

To uninstall: *Help > About > Installation Details*, select it, Uninstall.

<details>
<summary><b>Fallback: dropins</b> (older Eclipse, locked-down installations, no network)</summary>

1. close Eclipse
2. copy `alien.rainbow.indentguide_1.0.0.jar` into the `dropins/` folder of
   your Eclipse installation — the jar as-is, don't unpack it
3. start Eclipse once with `eclipse -clean`, otherwise the bundle cache hides
   the new file
4. check under *Window > Preferences > General > Editors > Text Editors*

Installed this way, the plug-in won't appear in Installation Details and has
to be removed by deleting the jar.
</details>

**Upgrading from the original plug-in?** Remove
`jp.sourceforge.pdt_tools.indentGuide` first — the symbolic name differs, so
both would draw guides at the same time. Preferences are not carried over.

## Requirements

Tested on Eclipse 2020-03 and 2026-06. Compiled with `javac --release 8`, so
it should also load on much older installations (3.6 and later) — an API newer
than Java 8 fails at compile time rather than throwing `NoSuchMethodError` at
runtime. Requires Java 8 or later.

Verified to work with *Show only selected element* and *Clone Editor*.

Bug reports welcome, especially from versions I haven't tested.

## Layout

    alien.rainbow.indentguide/            plug-in
    alien.rainbow.indentguide.feature/    feature
    alien.rainbow.indentguide.updateSite/ generated p2 repository

## History

The painting code originates from Eclipse's own `WhitespaceCharacterPainter`
(Anton Leherbauer, Wind River Systems, and contributors), which was adapted
into an indent guide as `jp.sourceforge.pdt_tools.indentGuide`, later
re-uploaded by [kiritsuku](https://github.com/kiritsuku/IndentGuide) when the
original repository disappeared. This fork starts from there.
[Gerald Rosenberg](https://github.com/grosenberg/IndentGuide) also maintains an
active fork of the same plug-in.

## License

This repository is a fork. The original work stays under the MIT License,
Copyright (c) 2014 Simon Schaefer; parts of the painter derive from the Eclipse
Platform and stay under the Eclipse Public License v1.0, Copyright (c) 2006,
2009 Wind River Systems, Inc., IBM Corporation and others. The modifications and
new files contributed by this fork are Copyright (c) 2026 Simone Platania and
are released under the Eclipse Public License v1.0.

The notices in the individual source files are authoritative. See `LICENSE`.
