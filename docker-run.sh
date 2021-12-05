#!/usr/bin/env bash

# user name for docker push (Change this to your docker user name)
user=$USER

# Starts tcs-deploy inside a docker container
# set host to the current ip
host=`ip route get 8.8.8.8 | awk '{print $NF; exit}'`

# Start the application
docker run -d -P -p 9753:9753 --name tcs-deploy $user/tcs-deploy --local conf/McsEncPkContainer.conf || exit 1

