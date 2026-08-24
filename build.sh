#!/bin/sh
#
# Builds the plug-in and the feature and regenerates docs/, the p2 repository
# GitHub Pages serves. docs/ is the only copy of the update site: everything
# under docs/features and docs/plugins is replaced, site.xml and .nojekyll and
# index.html are kept.
#
# Needs a JDK 17 or newer to read the Eclipse jars it compiles against, and an
# Eclipse installation to run the p2 publisher. The bytecode is Java 8 either
# way, so the plug-in keeps loading on the old releases.
#
#   ./build.sh [path-to-eclipse]
#
set -eu

VERSION=$(sed -n 's/^Bundle-Version: *//p' alien.rainbow.indentguide/META-INF/MANIFEST.MF | tr -d '\r')
# the attribute on its own line, not the version="1.0" of the XML declaration
FEATURE_VERSION=$(sed -n 's/^[[:space:]]*version="\([0-9][^"]*\)".*/\1/p' \
	alien.rainbow.indentguide.feature/feature.xml | head -1)
if [ "$VERSION" != "$FEATURE_VERSION" ]; then
	echo "bundle is $VERSION but feature is $FEATURE_VERSION; bump both" >&2
	exit 1
fi

ROOT=$PWD
# The publisher is a Java program: it needs C:/... , not the /c/... of the
# shell, and it reports success either way after writing to the wrong place.
ROOT_URI=$(cygpath -m "$ROOT" 2>/dev/null || printf '%s' "$ROOT")

ECLIPSE=${1:-${ECLIPSE_HOME:-}}
LAUNCHER=""
if [ -n "$ECLIPSE" ]; then
	for candidate in "$ECLIPSE/eclipsec.exe" "$ECLIPSE/eclipse"; do
		if [ -x "$candidate" ]; then
			LAUNCHER=$candidate
			break
		fi
	done
fi
if [ -z "$LAUNCHER" ]; then
	echo "usage: $0 <path-to-eclipse>   (or set ECLIPSE_HOME)" >&2
	exit 1
fi

# The bundles to compile against, taken from whatever the Eclipse being used
# has in its bundle pool. Any reasonably recent versions will do: the plug-in
# only touches API that has been stable since 3.x.
POOL=${ECLIPSE_POOL:-$HOME/.p2/pool/plugins}
CP=""
for want in org.eclipse.swt.win32.win32.x86_64 org.eclipse.jface org.eclipse.jface.text \
	org.eclipse.text org.eclipse.ui.workbench org.eclipse.ui.workbench.texteditor \
	org.eclipse.core.runtime org.eclipse.equinox.common org.eclipse.osgi \
	org.eclipse.core.commands org.eclipse.equinox.registry org.eclipse.equinox.preferences \
	org.eclipse.core.jobs org.eclipse.ui org.eclipse.core.contenttype; do
	jar=$(ls -1 "$POOL/${want}_"*.jar 2>/dev/null | sort | tail -1)
	if [ -z "$jar" ]; then
		echo "missing $want under $POOL" >&2
		exit 1
	fi
	CP="$CP;$(cygpath -m "$jar" 2>/dev/null || echo "$jar")"
done
CP=${CP#;}

BUILD=$(mktemp -d)
trap 'rm -rf "$BUILD"' EXIT

echo "compiling $VERSION"
mkdir -p "$BUILD/classes"
javac -nowarn -encoding UTF-8 --release 8 -cp "$CP" -d "$BUILD/classes" \
	$(find alien.rainbow.indentguide/src -name '*.java')

echo "packaging"
P="$BUILD/plugin"
mkdir -p "$P"
cp -r "$BUILD/classes/." "$P/"
cp -r alien.rainbow.indentguide/META-INF alien.rainbow.indentguide/OSGI-INF "$P/"
cp alien.rainbow.indentguide/plugin.xml alien.rainbow.indentguide/LICENSE "$P/"
mkdir -p "$P/src"
cp -r alien.rainbow.indentguide/src/. "$P/src/"
find "$P/src" -name '*.java' -delete
find "$P/src" -type d -empty -delete
# resources sit next to the classes as well, that is where the code loads them
cp alien.rainbow.indentguide/src/jp/sourceforge/pdt_tools/indentguide/preferences/messages.properties \
	"$P/jp/sourceforge/pdt_tools/indentguide/preferences/"

rm -rf docs/features docs/plugins
mkdir -p docs/features docs/plugins
(cd "$P" && jar --create --file "$ROOT/docs/plugins/alien.rainbow.indentguide_$VERSION.jar" \
	--manifest META-INF/MANIFEST.MF $(ls -A | grep -v '^META-INF$'))

F="$BUILD/feature"
mkdir -p "$F"
cp alien.rainbow.indentguide.feature/feature.xml alien.rainbow.indentguide.feature/p2.inf "$F/"
cp LICENSE "$F/"
(cd "$F" && jar --create --file "$ROOT/docs/features/alien.rainbow.indentguide.feature_$VERSION.jar" .)

# site.xml names the feature jar by version, and the publisher reads it to
# decide what goes in the category; left stale it silently publishes nothing.
sed -i.bak -E \
	"s|(features/alien\.rainbow\.indentguide\.feature_)[0-9][^\"]*(\.jar)|\1$VERSION\2|; \
	 s|(id=\"alien\.rainbow\.indentguide\.feature\" version=\")[^\"]*|\1$VERSION|" \
	docs/site.xml
rm -f docs/site.xml.bak
grep -q "_$VERSION.jar" docs/site.xml || {
	echo "site.xml does not reference $VERSION" >&2
	exit 1
}

echo "publishing p2 metadata"
rm -f docs/artifacts.jar docs/content.jar
"$LAUNCHER" -nosplash -consolelog \
	-application org.eclipse.equinox.p2.publisher.UpdateSitePublisher \
	-metadataRepository "file:$ROOT_URI/docs" \
	-artifactRepository "file:$ROOT_URI/docs" \
	-source "$ROOT_URI/docs" \
	-compress -publishArtifacts 2>&1 | grep -E 'Generation completed|error|Error' || true

test -f docs/content.jar || { echo "publisher produced no metadata" >&2; exit 1; }

echo "archiving"
rm -f RainbowIndentGuide-*-updatesite.zip
(cd docs && jar --create --no-manifest \
	--file "$ROOT/RainbowIndentGuide-$VERSION-updatesite.zip" \
	artifacts.jar content.jar site.xml features plugins)

echo "done: docs/ and RainbowIndentGuide-$VERSION-updatesite.zip are at $VERSION"
