## Create config

```
kubectl create configmap notary-config --from-file=files/node.conf --from-file=files/network-parameters --namespace manual
kubectl create secret generic notary-keys --from-file=files/certificates/nodekeystore.jks --from-file=files/certificates/sslkeystore.jks --from-file=files/certificates/truststore.jks --namespace manual
kubectl apply -f volume.yaml -n manual
```

## Install

```
helm install (--debug) (--dry-run) --name notary --namespace manual . -f STAGE.yaml
```

### Upload files to container

The required files are added through the config maps, since the container will die without it.

After installation and when the container is running, files can be installed.
The uploaded files will be placed in the PVC and will survive restarts.

Copy additional node info's

```
kubectl cp path/to/nodeInfo/* [NOTARY-POD]:/opt/nuts/additional-node-infos/
```

This last step has to be done for each node that's added to the network until the discovery service is live.

### To list contents

```
kubectl exec [NOTARY-POD] -n manual ls /opt/nuts/
```

## Upgrade

```
helm upgrade (--debug) (--dry-run) notary . -f STAGE.yaml
```

The kubernetes node might be too small to host 2 containers, in that case scale down and up...