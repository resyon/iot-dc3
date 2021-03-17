#! /usr/bin/env bash

test  $# -lt 1  && echo "usuage:  $0 [build/clean]"

# function checkEnv(){

# }
function build () {
    echo 'start to package'
    mvn package
    cd ./dc3
    docker-compose build
    
    cd ../dc3-web
    sudo npm install -g cnpm --registry=https://registry.npm.taobao.org
    cnpm install
    npm run build
    cd ./dc3
    docker-compose build    
}

function clean(){
    mvn clean
}

case $1 in 
    'build' )
       build
        ;;
    'clean')
        clean
        ;;
esac
    

