# rhods-test-clojure

Small Clojure sample app to experiment with RHODA (Red Hat OpenShift Database Access)

      oc new-project rhoda-test-clojure
      oc import-image mpiech/s2i-clojure --confirm
      oc new-build mpiech/s2i-clojure~https://github.com/mpiech/rhoda-test-clojure
      oc new-app rhoda-test-clojure
      oc expose service/rhoda-test-clojure

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein ring server

## License

Copyright © 2022
