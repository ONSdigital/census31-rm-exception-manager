# census-rm-exception-manager
This service manages exceptions raised by other Census services.

Podman and Docker are both supported for building and running the application.
By default the Makefile will use `docker` unless you are on an `arm64` architecture (e.g. M1/M2 Mac) in which case it will use `podman`.
You can override this by setting the `DOCKER` environment variable to either `docker` or `podman`.
For example, to force using `docker` on an M1/M2 Mac:
```shell
DOCKER=docker make <command>
```


## How to run
Build it using `make install` and then execute the JAR file or run in your favourite debugger.

## How to test
Run `make test`
