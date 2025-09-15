# Run multivox `virtex` with Docker (macOS + xQuartz)

1. Prerequisites (once)
- Install XQuartz
- Install Docker for Mac
- brew install `socat`


1. Start XQuartz and allow network clients
- open xquartz -> settings -> preferences -> settings -> check `Allow connections from network clients`
- quit and reopen xquartz
- in xquartz, Applications -> Terminal (xterm) -> run:
```shell
xhost +
```

2. Build and run inside a container
- from your multivox repo root in a normal macOS terminal:
```shell
docker run -it --rm \
  --name multivox-run \
  -e DISPLAY=host.docker.internal:1 \
  -e LIBGL_ALWAYS_INDIRECT=1 \
  -e LIBGL_ALWAYS_SOFTWARE=1 \
  -e MESA_LOADER_DRIVER_OVERRIDE=llvmpipe \
  -v "$PWD":/src \
  --workdir /src \
  ubuntu:22.04 bash -lc '
set -e
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  git build-essential cmake pkg-config \
  x11-apps mesa-utils \
  libx11-dev libxext-dev libxi-dev libxxf86vm-dev libxrandr-dev libxinerama-dev \
  libegl1-mesa-dev libgles2-mesa-dev libgl1-mesa-dev libgl1-mesa-dri
rm -rf build && mkdir build && cd build
cmake -DMULTIVOX_GADGET=vortex ..
cmake --build . --target virtex tesseract fireworks
./virtex -g l -s 48 -w 800 600 -b 1
'
```
- leave this terminal running; it keeps the virtex window open.
- in a second terminal shell, feed frames to virtex:
```shell
docker exec -it multivox-run bash -lc 'cd /src/build && ./fireworks'
```
3. Stop and clean
- close the virtex window or Ctrl + C in the virtex window
- or alternatively , `docker stop multivox-run` in a terminal shell
