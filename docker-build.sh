#!/bin/sh
#
# Builds a docker image for tcs-deploy.
#

# Get the IP address
host=`uname -n`

# user name for docker push (Change this to your docker user name)
user=$USER

# version tag
version=latest

sbt tcs-deploy/docker:publishLocal || exit 1
sbt tcs-deploy/docker:stage || exit 1
cd tcs-deploy/target/docker/stage
#DOCKER_BUILDKIT=1 docker build -t $user/tcs-deploy:$version .  || exit 1

# Push to docker hub...
# docker push $user/tcs-deploy:latest

