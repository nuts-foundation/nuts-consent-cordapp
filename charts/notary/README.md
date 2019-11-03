### Create config

```
kubectl create configmap notary-config --from-file=files/node.conf --from-file=files/nodeInfo-74A33A2848FC5E331B61005443ED44CB4A047F49167A3D9850BA81296A4EDAFF --from-file=files/network-parameters --namespace development
kubectl create secret generic notary-keys --from-file=files/certificates/nodekeystore.jks --from-file=files/certificates/sslkeystore.jks --from-file=files/certificates/truststore.jks --namespace development
kubectl create configmap network-nodes --from-file=files/additional-node-infos/nodeInfo-74A33A2848FC5E331B61005443ED44CB4A047F49167A3D9850BA81296A4EDAFF --namespace development
```

todo nodeInfo and additionalNodeInfos

### Install

```
helm install (--debug) (--dry-run) --name notary --namespace development . -f values.yaml
```