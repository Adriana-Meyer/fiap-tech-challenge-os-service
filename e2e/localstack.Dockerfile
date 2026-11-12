# LocalStack with the saga topology baked in. The init script is copied into the
# image, instead of bind-mounted, so this works wherever the repository lives
# (Docker Desktop on macOS cannot mount folders under ~/Desktop without extra
# permissions) and the script is executable without a chmod on the host.
FROM localstack/localstack:3.8.1
COPY --chmod=755 localstack-init/ /etc/localstack/init/ready.d/
