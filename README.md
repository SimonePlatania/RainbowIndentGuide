# Rainbow Indent Guide

🌈 Multi-level rainbow indent guides for Eclipse — a fork of [Indent Guide](https://github.com/kiritsuku/IndentGuide), rebuilt to work on current releases. 🌈

[![Download](https://img.shields.io/badge/Download-1.2.0-2C2255?style=for-the-badge)](https://github.com/SimonePlatania/RainbowIndentGuide/releases/latest)
[![Eclipse Marketplace](https://img.shields.io/badge/Eclipse%20Marketplace-Install-2C2255?style=for-the-badge&logo=eclipseide&logoColor=white)](https://marketplace.eclipse.org/marketplace-client-intro?mpc_install=7554850)

<img width="1536" height="1024" alt="Rainbow indent guides in the Eclipse Java editor" src="https://github.com/user-attachments/assets/800262c6-f1c7-4642-af5b-149a587b743a" />

## Screenshots

<img src="https://github.com/user-attachments/assets/cd55803e-d63f-4815-85f7-6e427205c902" width="100%" alt="" />

<table>
  <tr>
        <td width="50%">
      <img src="https://github.com/user-attachments/assets/46e90030-c733-4b36-938c-a9be34c7c702" alt="Active guide lit up" />
            <sub><b>Deep nesting</b> — one colour per level, so eight levels stay readable.</sub>
    </td>
    <td width="50%">
      <img src="https://github.com/user-attachments/assets/60f5c342-f47d-403d-aa09-079a44af6086" alt="Deeply nested code with rainbow guides" />
            <sub><b>Active block</b> — the guide holding the caret keeps its colour and lights up.</sub>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <img src="https://github.com/user-attachments/assets/480c72a1-411f-425e-9200-2bc55afbd476" alt="Nested call with rainbow parentheses" />
      <sub><b>Rainbow parentheses</b> — nine levels of nested calls, each bracket matched to its guide.</sub>
    </td>
    <td width="50%">
      <img src="https://github.com/user-attachments/assets/02ee53b3-7b57-4d5d-a77d-8e9b52141c1d" alt="Guide blinking as indentation is fixed" />
      <sub><b>Restore blink</b> — a greyed guide flashes as it takes its colour back.</sub>
    </td>
  </tr>
</table>

<img src="https://github.com/user-attachments/assets/cd55803e-d63f-4815-85f7-6e427205c902" width="100%" alt="" />

## Features

- one color per indentation level (rainbow), cycled past the seventh
- the braces of a block are repainted in the color of its guide, so the two
  read as one shape
- matching round parentheses are colored by nesting depth with their own
  configurable seven-color palette, independent from braces and guides
- click a guide to light and pin it; a click away from every guide releases it.
  The lit guide keeps the color of its level and turns it up — brighter and
  deeper, not washed out towards white
- following the caret is optional and disabled by default
- guides follow the brace structure: they start below the line opening the
  block and reach the brace closing it
- a multi-line call gets one guide, at the indentation of the line opening it:
  the columns its continuation lines are aligned to are alignment, not nesting,
  and the guides of the blocks around it run through unbroken
- blank lines and comments no longer break the vertical lines
- backspace at the indentation of a comment written under a brace moves it onto
  the line above, instead of shaving a level off an indentation that was
  already right. The same for code written hard against a brace, off until it
  is asked for. A brace on a line of its own never moves: it keeps walking left
  to the level of the block it closes
- each guide is stroked in one piece over the lines it spans, so thin or barely
  opaque lines stay clean instead of breaking up into dots
- sub-pixel guide placement, so the lines do not drift right on deep levels
  when DPI scaling is on
- optional greying of the single guide that does not line up with the brace
  opening or closing its block; when the indentation is fixed the guide blinks
  white for a moment as it takes its color back
- the tabs restored with the workbench are painted at startup, without having
  to be closed and reopened
- the common content types (Java, XML, properties, JSP, HTML, CSS, JS, PHP,
  C/C++) are enabled out of the box

Everything is configurable under
*Window > Preferences > General > Editors > Text Editors > Indent Guide*,
with the colors on the **Style** subpage. Changes apply immediately, without
reopening editors.

## Installation

### Eclipse Marketplace

<a href="https://marketplace.eclipse.org/marketplace-client-intro?mpc_install=7554850">
  <img src="https://marketplace.eclipse.org/modules/custom/eclipsefdn/eclipsefdn_marketplace/images/btn-install.svg" alt="Drag to your running Eclipse workspace to install Rainbow Indent Guide" width="80">
</a>

Drag the button onto a running Eclipse window. Requires the Marketplace Client,
which most Eclipse packages ship with.

### From the update site

    https://simoneplatania.github.io/RainbowIndentGuide/

1. *Help > Install New Software...*
2. *Add...*, paste the address, give it any name
3. tick **Rainbow Indent Guide**, then Next, accept the license, Finish
4. restart when prompted

Eclipse remembers the site and offers later versions under *Help > Check for
Updates*.

### From the archive

For an installation with no network access. Download
`RainbowIndentGuide-1.2.0-updatesite.zip` from the
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
2. copy `alien.rainbow.indentguide_1.2.0.jar` into the `dropins/` folder of
   your Eclipse installation — the jar as-is, don't unpack it
3. start Eclipse once with `eclipse -clean`, otherwise the bundle cache hides
   the new file
4. check under *Window > Preferences > General > Editors > Text Editors*

Installed this way, the plug-in won't appear in Installation Details and has
to be removed by deleting the jar.
</details>

**Upgrading from another Indent Guide?** Remove it first. This fork still
contributes the extension ids of the original, so two of them installed side by
side give duplicate preference pages and two painters drawing over each other.
Since 1.1.0 p2 refuses the install rather than let that happen: the feature
declares `jp.sourceforge.pdt_tools.indentGuide` and Certiv Analytics'
`net.certiv.tools.indentguide` as conflicting, both as features and as bare
bundles. Preferences are not carried over.

## Requirements

Tested on Eclipse 2020-06, 2024-09 and 2026-06. Compiled with `javac --release 8`, so
it should also load on much older installations (3.6 and later) — an API newer
than Java 8 fails at compile time rather than throwing `NoSuchMethodError` at
runtime. Requires Java 8 or later.

Verified to work with *Show only selected element* and *Clone Editor*.

Bug reports welcome, especially from versions I haven't tested.

## Troubleshooting

**No guides in a file.** Guides are drawn per content type, and matching is on
exact equality, so a file only gets them if it is opened with the editor
registered for its type. A `.jsp` on an Eclipse without WTP has no JSP content
type and opens in the plain text editor, which is not the same thing as a JSP
editor. *Right-click → Open With* shows which one is in use. Enabling **Text**
in the preferences covers anything that falls back to the generic editor, at
the cost of drawing guides in files where they are of little use.

**No guides anywhere.** Check *Help > About > Installation Details > Plug-ins*
and look for `alien.rainbow.indentguide`. If it is listed as `INSTALLED`
rather than `ACTIVE`, the bundle was found but could not be resolved; if it is
not listed at all, it never got picked up — start Eclipse once with
`-clean`.

**Guides in the wrong place, or drawn twice.** Another Indent Guide plug-in is
probably still installed. Since 1.1.0 p2 refuses the install, but an older
copy dropped into `dropins/` bypasses p2 entirely and will keep painting.

**Preference changes don't show up.** They apply immediately to open editors.
If nothing changes, the preference store is likely a different one — check you
are not looking at a second workspace.

## Layout

    alien.rainbow.indentguide/         plug-in
    alien.rainbow.indentguide.feature/ feature
    docs/                              p2 repository, release JARs and ZIP
    build.sh                           regenerates docs/ and the release zip

`docs/` is the only copy of the update site, and it is what
*Help > Install New Software...* reads over HTTP. Two things in it are load
bearing and easy to lose: `.nojekyll`, without which GitHub runs the directory
through Jekyll and drops anything starting with an underscore, and `site.xml`,
which the publisher reads to decide what goes in the category.

To cut a release, bump `Bundle-Version` in the plug-in manifest and `version`
in `feature.xml` to match, then:

    ./build.sh /path/to/eclipse

It compiles with `--release 8`, rebuilds both jars, rewrites the version in
`site.xml`, republishes the p2 metadata and repacks the archive. It refuses to
run if the two versions disagree.

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
