nuts-consent-cordapp
####################

Discovery Consent Cordapp by the Nuts foundation for distributing Consent records across nodes.

.. image:: https://circleci.com/gh/nuts-foundation/nuts-consent-cordapp.svg?style=svg
    :target: https://circleci.com/gh/nuts-foundation/nuts-consent-cordapp
    :alt: Build Status

.. image:: https://codecov.io/gh/nuts-foundation/nuts-consent-cordapp/branch/master/graph/badge.svg
    :target: https://codecov.io/gh/nuts-foundation/nuts-consent-cordapp

.. image:: https://api.codeclimate.com/v1/badges/52ce5adf2112d069397a/maintainability
   :target: https://codeclimate.com/github/nuts-foundation/nuts-consent-cordapp/maintainability
   :alt: Maintainability

The consent cordapp is written in Kotlin and can be build by Gradle.

Dependencies
************

Since the consent cordapp depends on Corda, Java 1.8 is needed. For the Oracle sdk, this means that your version needs to be > 1.8 update 151.
This can give problems on several linux distro's. In that case use the latest OpenJDK 1.8.

The project is build with Gradle. A gradle wrapper is present in the project.

Running tests
*************

Tests can be run by executing

.. code-block:: shell

    ./gradlew test

Building
********

Jars can be build by executing

.. code-block:: shell

    ./gradlew jar

Docker
******

To build locally

.. code-block:: shell

    docker build . -f docker/Dockerfile-dev

The ``nutsfoundation/nuts-consent-cordapp:latest-dev`` docker image can be used to run 3 nodes locally. Checkout :ref:`nuts-network-local-development-docker` for setting up a complete environment with ``docker-compose``.

README
******

The readme is auto-generated from a template and uses the documentation to fill in the blanks.

.. code-block:: shell

    ./generate_readme.sh

Documentation
*************

To generate the documentation, you'll need python3, sphinx and a bunch of other stuff. See :ref:`nuts-documentation-development-documentation`
The documentation can be build by running

.. code-block:: shell

    /docs $ make html

The resulting html will be available from ``docs/_build/html/index.html``

Release
*******

Both the flows and contract libs are published to maven central (through OSS Sonatype). Before you can release and sign the jars, you need the following things:

- a valid gpg setup
- a published gpg key
- a sonatype account linked to nl.nuts

You can release libraries through:

.. sourcecode:: shell

    ./gradlew uploadArchives

Then go to https://oss.sonatype.org and *close* and *release* the libs. More info can be found on https://central.sonatype.org/pages/releasing-the-deployment.html.

.. note::

    It seems signing require Oracles JVM! So openjdk won't work.

Configuration
*************

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

