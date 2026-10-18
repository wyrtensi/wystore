#!/bin/sh
# Forced command for the GitHub Actions deploy key (see authorized_keys of the
# wydeploy user). The key can do exactly two things:
#   rsync ... <release>/   upload a build into releases/<release> (write-only)
#   activate <release>     point "current" at a complete release, keep the 5 newest
# Installed as /usr/local/bin/wystore-deploy.
set -eu

site=/opt/wystore/site
command=${SSH_ORIGINAL_COMMAND:-}

case "$command" in
  "rsync --server "*)
    exec /usr/bin/rrsync -wo "$site/releases"
    ;;
  "activate "*)
    release=${command#activate }
    case "$release" in
      ""|*[!a-z0-9-]*) echo "invalid release name" >&2; exit 1 ;;
    esac
    for page in index.html en/index.html 404.html sitemap.xml; do
      if [ ! -f "$site/releases/$release/$page" ]; then
        echo "release $release is incomplete: no $page" >&2
        exit 1
      fi
    done
    ln -sfn "releases/$release" "$site/current.next"
    mv -T "$site/current.next" "$site/current"
    # Release names start with a UTC timestamp, so name order is age order.
    ls -1 "$site/releases" | sort -r | tail -n +6 | while read -r old; do
      [ "$old" = "$release" ] || rm -rf -- "$site/releases/$old"
    done
    echo "activated $release"
    ;;
  *)
    echo "command not allowed" >&2
    exit 1
    ;;
esac
