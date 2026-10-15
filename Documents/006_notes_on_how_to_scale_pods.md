## Scaling the pods

Deleting the pods using the kubectl delete command restarts the pod. Scaling the pod to 0 helps terminate the pod.

```bash
kubectl scale deploy --all --replicas=0
kubectl scale statefulset --all --replicas=0
```
To restart the pod, scale it back to 1

```bash
kubectl scale deploy --all --replicas=1
kubectl scale statefulset --all --replicas=1
```