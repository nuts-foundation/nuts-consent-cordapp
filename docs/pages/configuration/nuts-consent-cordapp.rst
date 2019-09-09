.. _nuts-consent-cordapp-configuration:

Nuts consent cordapp configuration
##################################

.. marker-for-readme

The basic node.conf inside the Cordap base directory should look similar like this:

.. code-block:: yaml

    myLegalName="O=Nuts,C=NL,L=Groenlo,CN=nuts_corda_development_dahmer"
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

Both the ``doormanURL`` and ``networkMapURL`` must point to the location where *Nuts Discovery* is running. The ``p2pAddress`` is the endpoint that must be exposed to the outside world and which is added to the *Nuts registry*. The ``rpcSettings`` property is used for exposing the rpc endoint used by *Nuts consent bridge*.

The ``myLegalName`` is the identity of the node and must be unique. It follows the x500 name convention. This is also the identiy that is added to the *Nuts registry* consent endpoint.

Signed libraries
****************

When ``devMode=false`` Corda requires signed or whitelisted jars containing the digital contracts. At this point it's undecided if Nuts is going to start with whitelisted jars or is it going to start with signed jars. When running with ``devMode=true`` this is of no concern.

Additional info
***************

See https://docs.corda.net/corda-configuration-file.html

