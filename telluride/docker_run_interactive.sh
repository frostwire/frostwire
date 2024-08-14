# Detect the operating system
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    # Linux OS
    CPUS=$(nproc)
elif [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    CPUS=$(sysctl -n hw.ncpu)
elif [[ "$OSTYPE" == "cygwin" ]] || [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]]; then
    # Windows (Cygwin, MinGW, or Git Bash)
    CPUS=$(echo %NUMBER_OF_PROCESSORS%)
else
    echo "Unsupported OS type: $OSTYPE"
    exit 1
fi

docker \
 run \
 --cpus ${CPUS} \
 -v "$PWD/../telluride:/telluride-ubuntu" \
 -it telluride-ubuntu

