#!/bin/sh
# Starts as root: a Fly volume mount may be owned by root, so give the data dir to the app user (1001), then run the app as that user.
set -e
chown 1001:0 /opt/docker/data
exec setpriv --reuid=1001 --regid=0 --clear-groups /opt/docker/bin/ride-the-lightning "$@"
