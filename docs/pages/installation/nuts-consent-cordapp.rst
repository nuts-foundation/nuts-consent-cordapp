.. _nuts-consent-cordapp-installation:

Nuts consent cordapp installation
#################################

The corda part of the Nuts node (the registry and bridge are the two other parts) requires the following minimal file setup:

.. code-block:: none

    .
    ├── certificates
        └── network-root-truststore.jks
    ├── cordapps
        ├── contract-X.Y.Z.jar
        └── flows-X.Y.Z.jar
    ├── corda-4.0.jar
    └── node.conf

This structure will be extended when the node is run for the first time.

Truststore
**********

The truststore must be obtained from Nuts or if you run a local network, you can copy it from :ref:`nuts-discovery-configuration`.

Cordapps
********

The ``contract`` and ``flows`` jars are published on maven central. Obtain a copy: https://search.maven.org/search?q=nl.nuts or via browsing the repo: https://repo1.maven.org/maven2/nl/nuts/consent/cordapp/.

As an alternative you can use your own build, that will only work for a local network.

Corda.jar
*********

Obtain a copy from here https://search.maven.org/search?q=a:corda or via browsing here: https://repo1.maven.org/maven2/net/corda/corda/4.0/.

Currently we're using version 4.0

Node.conf
*********

This is the most interesting part of the setup. See :ref:`nuts-consent-cordapp-configuration`

First run
*********

Before running, you can check the configuration with:

.. code-block:: shell

    java -jar corda.jar --network-root-truststore cordacadevpass validate-configuration

The first time a node is started, it must be run with the additional ``initial-registration`` command:

.. code-block:: shell

    java -jar corda.jar --network-root-truststore cordacadevpass initial-registration

This will generate the needed keys and apply for a certificate at the network authority.

Additional info
***************

Additional info on the folder structure can be found on the corda documentation: https://docs.corda.net/node-structure.html and https://docs.corda.net/node-commandline.html