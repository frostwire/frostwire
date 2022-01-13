# One Step Build (It will be cached if nothing changed)
./docker_build_image.sh

# Mounts this repo's folder as a volume in the container's /telluride-ubuntu folder
# Then executes the build scripts
docker \
 run \
 --cpus 16 \
 -v "$PWD/../telluride:/telluride-ubuntu" \
 -it telluride-ubuntu \
 /bin/bash -c "cd telluride-ubuntu && ./configure_update.sh && ./build.sh"

