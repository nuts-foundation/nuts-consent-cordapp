.. _nuts-consent-cordapp-flows:

Supported flows
===============

.. contents:: Flows:
    :depth: 1
    :local:


New Consent Request
-------------------
This flow is initiated when no *DPC* record exists yet. The flow creates a *ConsentRequestState* without any validation proofs of any Party. All nodes in the Nuts network must listen to these state changes and start the approval process in *Service Space*. This will validate if the record is correct and if the patient is indeed a patient for the given Custodian. An attachment must be uploaded to the initiating node or the transaction will fail.

Accept New Consent Request
--------------------------
This is the counter party flow for the *New Consent Request* flow. It makes sure that the other node in the network perform the same contract checks as the initiating node has done.

Accept Consent Request
----------------------
More on this later