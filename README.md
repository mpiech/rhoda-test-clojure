# rhoda-test-clojure

Small Clojure sample app to experiment with RHODA (Red Hat OpenShift Database Access)

(This is a work in progress; there's not yet enough explanation of the data structure and/or sample data provided here to get you a fully running app, but you can at least see what the code looks like.)

## Running on OpenShift

    oc new-project rhoda-test-clojure
    oc import-image mpiech/s2i-clojure --confirm
    oc new-build mpiech/s2i-clojure~https://github.com/mpiech/rhoda-test-clojure
    oc new-app rhoda-test-clojure \
       -e GMAPS_KEY="xxx" \
       -e ATLAS_HOST="xxx.yyy.mongodb.net" \
       -e ATLAS_USERNAME="atlas-db-user-xxx" \
       -e ATLAS_PASSWORD="xxx" \
       -e ATLAS_DB="xxx" \
       -e PGHOST="p.xxx.db.postgresbridge.com" \
       -e PGUSER="postgres" \
       -e PGPASSWORD="xxx" \
       -e PGDB="postgres"
    oc expose service/rhoda-test-clojure

You'll need a google maps API key, a SQL database on Crunchy Bridge and a MongoDB on Mongo Atlas (structure and sample data to be added here later).

## Running Locally

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein ring server

in the rhoda-test-clojure directory

## License

Copyright © 2022
