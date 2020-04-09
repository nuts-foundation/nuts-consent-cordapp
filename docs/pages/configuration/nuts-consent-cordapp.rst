.. _nuts-consent-cordapp-configuration:

Nuts consent cordapp configuration
##################################

.. marker-for-readme

The basic node.conf inside the Cordap base directory should look similar like this:

.. code-block:: yaml

    myLegalName="O=Nuts,C=NL,L=Groenlo,CN=nuts_corda_development"
    emailAddress="info@nuts.nl"
    devMode=false
    devModeOptions {
      allowCompatibilityZone = true
    }
    networkServices {
        doormanURL = "http://localhost:8080"
        networkMapURL = "http://localhost:8080"
    }
    p2pAddress="localhost:17886"
    rpcSettings {
        address="localhost:11003"
        adminAddress="localhost:11043"
    }
    rpcUsers=[]
    custom = {
        jvmArgs: [ "-Xmx1G", "-XX:+UseG1GC" ]
    }

Both the ``doormanURL`` and ``networkMapURL`` must point to the location where *Nuts Discovery* is running. The ``p2pAddress`` is the endpoint that must be exposed to the outside world and which is added to the *Nuts registry*. The ``rpcSettings`` property is used for exposing the rpc endoint used by *Nuts consent bridge*.

The ``myLegalName`` is the identity of the node and must be unique. It follows the x500 name convention. This is also the identiy that is added to the *Nuts registry* consent endpoint.

Since Corda 4.4 memory consumption has changed, the default 512m is no longer enough. The `custom` section is therefore mandatory:

.. code-block:: yaml

    custom = {
        jvmArgs: [ "-Xmx1G", "-XX:+UseG1GC" ]
    }

Database & Docker
*****************

By default Corda places the DB in the `baseDirectory` which, by default, is inside a docker container. This can be avoided by mounting the entire `baseDirectory` but this also means the cordapps and `corda.jar` have to be mounted as well. The Nuts cordapp image has these inside the image. Having to download them again is extra work, that's just annoying. Luckily it's also possible to put the DB in a different location. The default DB configuration is below:

.. code-block:: yaml

    dataSourceProperties = {
        dataSourceClassName = org.h2.jdbcx.JdbcDataSource
        dataSource.url = "jdbc:h2:file:"${baseDirectory}"/persistence;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0;LOCK_TIMEOUT=10000"
        dataSource.user = sa
        dataSource.password = ""
    }

By putting the DB in a sub directory it'll be easier to mount. For example changing above config to:

.. code-block:: yaml

    dataSourceProperties = {
        dataSourceClassName = org.h2.jdbcx.JdbcDataSource
        dataSource.url = "jdbc:h2:file:"${baseDirectory}"/data/persistence;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0;LOCK_TIMEOUT=10000"
        dataSource.user = sa
        dataSource.password = ""
    }

places the DB in a `/data` subdirectory. Which can then be mounted with:

.. code-block:: shell

    docker run \
        -v {{data_dir}}:/opt/nuts/data \
        -d \
        nuts-consent-cordapp:latest-dev

Signed libraries
****************

When ``devMode=false`` Corda requires signed or whitelisted jars containing the digital contracts. At this point it's undecided if Nuts is going to start with whitelisted jars or is it going to start with signed jars. When running with ``devMode=true`` this is of no concern.

Additional info
***************

See https://docs.corda.net/corda-configuration-file.html

