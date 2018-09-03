#!/bin/bash

set -e
set -o pipefail

cd "$(dirname "$0")"

when=$(git log -1 --pretty=format:'%ci')

mkdir tmp 2> /dev/null || true
touch tmp/log.old
git log --branches=\* --after="1 week ago" > tmp/log.new
diff tmp/log.new tmp/log.old > /dev/null 2>&1 && exit

echo "Posting new message"
msgfile="tmp/msg.txt"
cp /dev/null "$msgfile"
echo "title: Updates for $(basename $(pwd)) within the past week. $when"  >> "$msgfile"
echo "tags: utopian-io steemdev java steemj git-log"  >> "$msgfile"
echo "format: markdown"  >> "$msgfile"
echo "" >> "$msgfile"
echo "## $(basename $(pwd))" >> "$msgfile"
echo "Updates for $(basename $(pwd)) in the past week. $when" >> "$msgfile"
echo "" >> "$msgfile"
echo "" >> "$msgfile"
git log --branches=\* --after="1 week ago" | sed 's/<.*@.*>/[email redacted]/g' | sed 's/^commit /#### commit /g' >> "$msgfile"
echo "" >> "$msgfile"
echo "" >> "$msgfile"
java -jar ~/git/SteemCliPoster/build/libs/SteemCliPoster.jar \
	--auth-file ~/.steem/steem.properties \
	--file "$msgfile"
cp tmp/log.new tmp/log.old

