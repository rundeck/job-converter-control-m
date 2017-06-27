if [[ $# -eq 0 ]] ; then
    echo 'You need to provide a Control-M file or comma separated files'
    exit 0
fi

OUT=output
if [ -d "$OUT" ]; then
    printf '%s\n' "Removing Output Folder ($OUT)"
    rm -rf "$OUT"
fi

echo "CONTROL-M INPUT FILES: $1"

gradle resources -Pinput="$1"
gradle jobs -Pinput="$1"