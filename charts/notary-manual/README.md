### Create config

```
kubectl create configmap notary-config --from-file=files/node.conf --from-file=files/nodeInfo-74A33A2848FC5E331B61005443ED44CB4A047F49167A3D9850BA81296A4EDAFF --from-file=files/network-parameters --namespace development
kubectl create secret generic notary-keys --from-file=files/certificates/nodekeystore.jks --from-file=files/certificates/sslkeystore.jks --from-file=files/certificates/truststore.jks --namespace development
kubectl create configmap network-nodes --from-file=files/additional-node-infos/nodeInfo-74A33A2848FC5E331B61005443ED44CB4A047F49167A3D9850BA81296A4EDAFF  --from-file=files/additional-node-infos/nodeInfo-B89F7CCEB97BDF926D801C01138FF9B94E6CDC46B8B7FCA1770892C79FBAF8B9  --from-file=files/additional-node-infos/nodeInfo-91C262387328498D3664074E44E2B2AADA596D3FB2385509B02FBD0F29C413C1 --namespace development
kubectl apply -f volume.yaml -n manual
```

todo nodeInfo and additionalNodeInfos

### Install

```
helm install (--debug) (--dry-run) --name notary --namespace manual .
```