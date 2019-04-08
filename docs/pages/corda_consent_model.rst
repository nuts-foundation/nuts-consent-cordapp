.. _nuts-consent-cordapp-model:

Corda Consent Model
===================

When we talk about the *Corda Consent Model* we mean the *LinearState* and all attachments involved.
This model has little actual data (only encrypted data) and is only used to persist a state across parties.
Any constraints involved are to make sure encrypted data only arrives at the places it's meant to.

.. note::

    Even though data is encrypted and therefore it'll not be regarded as a data breach (GDRP) when leaked/stolen.
    It's still wise to not send it all over the place. Because when the used encryption algorithm is deemed unsecure, that old data that was send to unauthorised parties now suddenly becomes a data breach! And since data is immutable and will be stored for at least 15 years, nothing will be certain...

Attachments
-----------

`Corda attachments <https://docs.corda.net/tutorial-attachments.html>`_ are basically several files bundled in a single zip-file.
They are not part of the transaction, but their hash is. It's cryptographically hard to change a file so that it has the same hash as a different piece of data (not enough energy in the universe type hard).
Attachments can also be large since they'll contain an encrypted version of a FHIR bundle.
Not having to send the data all the time can be a time saver. :ref:`Custom flow logic <nuts-consent-cordapp-flows>` is responsible for sending or requesting different attachments from another party within the same transaction.

Attachments referenced in a Nuts consent transaction contain two files:

    - cipher_text.bin
    - metadata.json

The *cipher_text.bin* file is the encrypted FHIR consent bundle and the metadata contains data on how to decrypt the file and three custom fields:

    - domain, denotes the target audience for this attachment: medical, PGO or insurance nodes
    - previousAttachmentId, points to the previous attachment if the FHIR consent model has only be updated (increase of consent resource version)
    - period, the only part of the FHIR consent record that is also available without encryption. This is done to prevent sending attachments to other parties when they have already expired.

.. todo:

    the period property might prove to be unneeded when the check is done by service space anyway


metadata.json schema
--------------------

.. jsonschema:: ../schemas/cordapp-consent.json

Attachment lifecycle
--------------------

Attachments are immutable but the data they contain is not.
The underlying model can be updated or even additional consent records can be created for the same *DPC* record which can be valid at the same time.
This problem can be solved by using multiple attachments per *DPC* record.

.. note::

    A *DPC* record (Custodian, Subject, Actor) is unique but multiple consent records can be active at the same time.
    For example a patient can allow the hospital to access the involved care providers list present at a home care organisation.
    At some point in time the patient might be taken into the hospital for some treatment. Next to the list of care providers, the patient might want to allow the hospital to access the medical records at the home care organisation, but only temporary.
    An additional consent record will have to be present, which is only valid for a certain time, next to the 'base' consent record.

Only two actions are possible for consent records: *create* and *update*. A deletion is, in essence, just an update where the end date will be set to a certain point in time.
For auditing reasons the entire history must be kept as well. When a consent record gets updated, two new attachments are created for that transaction: the old consent record but now updated with an end date.
The underlying FHIR model will keep its ID and version number, and a new consent record valid from today onwards.
This underlying FHIR model for this new record will have the same ID but with an increased version number.
The updated record will also point towards the previous attachment so any party can view the history of the record.

Request/Accept state
--------------------

The creation or change of a *DPC* record is not a single transaction. Before a *DPC* record changes, a new ``ConsentRequestState`` needs to be created for the *DPC* record.
The involved parties will listen to these new states and will validate the attachments that come with it.
When the attachments are validated, a new transaction is started by the receiver to accept the new request and update the *DPC* record.
A big challenge with this flow is that the validation happens in *service space* and that the consent contract can not access the Nuts registry or validate that a certain public key is indeed the public key of a care organisation.
Validation of public keys in service space is sufficient since all encryption is connected to the public keys from the registry.
So any spoofed identities or wrong public keys will result in faulty encryption.
In the case a ``ConsentRequestState`` is wrongly accepted and finalized by a malicious node, other nodes will still log an error on processing the *DPC* record since its public keys will be unknown. This will also prevent those nodes from decrypting and storing the *DPC* record.

.. note:: Only one *ConsentRequestState* can exist per *DPC* record at any given time.

Additional parties
------------------

It is possible that a single care provider or organisation is using multiple pieces of software.
Something which is common if an organisation adopts the best-of-breed approach in software selection.
In that case you don't want to register additional consent. The way Nuts deals with this, is that a care organisation will have a unique set of keys which can be exported and uploaded to *Nuts service space*. When a new care organisation is registered for a Nuts node in the *Nuts registry* other nodes can act on this event.
If another node identifies the care organisation as a party that is registered at that node, it can pro-actively add the new Nuts node as a party to the existing consent records.
The same mechanism can be used for migration purposes as well.

For the consent cordapp this process is an update of the state but without any attachments changing or added/removed.
Only an involved party is added and all parties sign the transaction. All checks still need to be done by all involved parties.


Filtered transactions
---------------------

Corda supports a concept called 'transaction tear-off' or 'filtered transactions'. This allows flows to hide certain data for certain parties.
The transaction is still valid because the signing of the transaction by all the parties is done by signing a hash of the actual data.
This allows parties to see and sign a hash without seeing the data.
The entire transaction is constructed as a `Merkle tree <https://en.wikipedia.org/wiki/Merkle_tree>`_, where parts can be substituted by their hash.

This concept is used by Nuts to hide the BSN (Dutch national number) from PGO's (Personal health environment) and still allow the PGO to be part of the transaction.

.. note::

    The choice to let the PGO be part of the transaction is an important one.
    There's no shadow bookkeeping happening in order to distinguish between different environments and requirements on identifiers.
    Because the PGO and therefore the patient is part of the consent state, it'll always have the latest information on who can access their data!
