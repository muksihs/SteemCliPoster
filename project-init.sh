#!/bin/bash

cat > .gitignore << EOT
*~
*#
/build/
*.pydevproject
.metadata
.gradle
bin/
tmp/
*.tmp
*.bak
*.swp
*~.nib
local.properties
.settings/
.loadpath
.project
.externalToolBuilders/
*.launch
.cproject
.classpath
.buildpath
.target
.texlipse
.DS_store
/war/
EOT
sort .gitignore |uniq > .gitignore.tmp
mv -v .gitignore.tmp .gitignore

if [ -f "build.gradle" ]; then
	gradle :wrapper
	gradle :create-dirs
	find src | while read folder; do
		if [ ! -d "$folder" ]; then continue; fi
		touch "$folder"/.gitignore
	done
fi

