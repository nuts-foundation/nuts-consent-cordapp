.. _nuts-consent-cordapp-technical-flows:

Supported consent flows
=======================

Create Genesis Consent State
----------------------------

Enforcing unique constraints for distributed is extremely hard (if not impossible). The only tool we have is that we can enforce a state has to be consumed (just once) and this constraint is enforced by a notary. Using this knowledge, a genesis state has to be created for a *DPC* record before consent is added. The creation of the genesis state is node local, which makes it possible to apply a unique constraint through a normal database transaction. The creation of the genesis record is done by the corda bridge.

Create Consent Branch
---------------------

This flow is initiated when a *DPC* has to be changed. The flow creates a *ConsentBranch* state without any validation proofs of any party. All nodes in the Nuts network must listen to these state changes and start the approval process in *Service Space*. This will validate if the record is correct and if the patient is indeed a patient for the given Custodian. An attachment must be uploaded to the initiating node or the transaction will fail. Each party checks if the given proofs are valid and if the used public key is indeed the one used by that party (via Nuts Registry).

The *branch* and *merge* concepts are taken from Git. The idea is that when additional consent is added a merge should not be a problem (like new files in Git). But if an existing record is changed, this can only be done if there are no conflicts. The notary will define in which order merges are done in the case of a race-condition. Using this approach, adding multiple parallel records at once shouldn't be a problem.

Sign Consent Branch
-------------------

Whenever a *Consent Branch* is created, other parties have to approve the new request. This is done via the *Sign Consent Branch* flow. The flow adds the signatures of a care provider to the *ConsentBranch* state and starts a transaction. The transaction will then be validated by all nodes. Each care provider has to approve a *ConsentBranch* before it can be merged into a *DPC* record. The signatures are generated by the private key of the care provider, so any node can validate it in *service space*.

Merge Branch
------------

The *Merge Branch* flow takes a *ConsentState* and a *ConsentBranch* and merges it into a new *ConsentState*. It can only merge these two if the *ConsentBranch* only added new consent or if no other *Merge Branch* flow has been performed since the *Create Consent Branch* flow.