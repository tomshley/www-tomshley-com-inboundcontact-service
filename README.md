### Local DDL
```shell
docker exec -i yb-tserver-n1 /home/yugabyte/bin/ysqlsh -h yb-tserver-n1 -t < ddl-scripts/create_persistence_tables_yugabyte.sql
docker exec -i yb-tserver-n1 /home/yugabyte/bin/ysqlsh -h yb-tserver-n1 -t < ddl-scripts/create_projection_tables_yugabyte.sql
docker exec -i yb-tserver-n1 /home/yugabyte/bin/ysqlsh -h yb-tserver-n1 -t < ddl-scripts/create_query_tables_yugabyte.sql
```

### Setup Azure
```shell
az login
```
```shell
az account set --subscription 8a890cc0-2d47-407c-a6e5-c8cb6b37139e
```
```shell
az aks get-credentials --resource-group www-tomshley-com-k8s-resources --name www-tomshley-com-k8s --overwrite-existing
```
```shell
kubelogin convert-kubeconfig -l azurecli
```


### Setup GKE
```shell
gcloud auth login
```
```shell
gcloud container clusters get-credentials www-tomshley-com-cluster-1 --region us-east4 --project www-tomshley-com-20241010
```
### Setup Cluster Registry Auth
```shell
source ./.secure_files/.tfstate.env
```
```shell
export K8S_DOCKER_CONFIG_AUTH=$(echo "{ \"auths\": { \"https://$K8S_DOCKER_REGISTRY\":{ \"auth\":\"$(printf "$K8S_DOCKER_REGISTRY_USER:$K8S_DOCKER_REGISTRY_PASS" | openssl base64 -A)\" } }}")
export K8S_DOCKER_CONFIG_AUTH_BASE64=$(echo "$K8S_DOCKER_CONFIG_AUTH" | base64)
export YUGABYTEDB_HOSTNAME_BASE64=$(echo -n "$YUGABYTEDB_HOSTNAME" | base64 -w 0);
export YUGABYTEDB_USERNAME_BASE64=$(echo -n "$YUGABYTEDB_USERNAME" | base64 -w 0);
export YUGABYTEDB_PASSWORD_BASE64=$(echo -n "$YUGABYTEDB_PASSWORD" | base64 -w 0);
export KAFKA_BROKER_SERVER_BASE64=$(echo -n "$CONFLUENTCLOUD_BROKER_ENDPOINT" | base64 -w 0);
export KAFKA_CLUSTER_KEY_BASE64=$(echo -n "$CONFLUENTCLOUD_CLUSTER_KEY" | base64 -w 0);
export KAFKA_CLUSTER_SECRET_BASE64=$(echo -n "$CONFLUENTCLOUD_CLUSTER_PASSWORD" | base64 -w 0);
```

### Local Dev - Setup Minikube
```shell
minikube delete
minikube start                                                                                               
minikube addons enable ingress
minikube addons enable metrics-server

eval $(minikube -p minikube docker-env)

export KUBECONFIG=~/.kube/config
kubectl config set-context docker-desktop
```

### K8s Dashboard User
```shell
kubectl config set-context --current --namespace=kubernetes-dashboard
kubectl apply -f kubernetes/dashboard-user.yml
kubectl apply -f kubernetes/dashboard-binding.yml
kubectl -n kubernetes-dashboard create token admin-user | pbcopy 
```

### Setup Yugabyte
```shell
kubectl delete namespace www-tomshley-com-data-yb-1
kubectl apply -f kubernetes/data-yb.namespace.json
kubectl config set-context --current --namespace=www-tomshley-com-data-yb-1
helm repo add yugabytedb https://charts.yugabyte.com
helm repo update
```
### Deploy Yugabyte Minikube
```shell
kubectl delete namespace www-tomshley-com-data-yb-1
kubectl apply -f kubernetes/data-yb.namespace.json
kubectl config set-context --current --namespace=www-tomshley-com-data-yb-1
helm repo add yugabytedb https://charts.yugabyte.com
helm repo update
helm install www-tomshley-com-data-yb yugabytedb/yugabyte \
--version 2024.1.3 \
--set resource.master.requests.cpu=0.5,resource.master.requests.memory=0.5Gi,\
resource.tserver.requests.cpu=0.5,resource.tserver.requests.memory=0.5Gi,\
replicas.master=1,replicas.tserver=1,enableLoadBalancer=False --namespace www-tomshley-com-data-yb-1
```

### Deploy Yugabyte Prod GKE
```shell
helm install www-tomshley-com-data-yb yugabytedb/yugabyte \
--version 2024.1.3 --namespace www-tomshley-com-data-yb-1 --wait
```

```shell
kubectl --namespace www-tomshley-com-data-yb-1 exec -it yb-tserver-1 -- sh -c /home/yugabyte/bin/ysqlsh -h yb-tserver-n1 -t < ddl-scripts/create_persistence_tables_yugabyte.sql
```

If you want to set up port forwarding or a yql shell
```shell
kubectl port-forward service/yb-tservers 5433:5433
kubectl port-forward --namespace www-tomshley-com-data-yb-1 yb-tserver-0 5433:5433
```

To Auth With Docker to Push
```shell
echo "$DOCKER_PUSH_REGISTRY_PASS" | docker logout $DOCKER_PUSH_REGISTRY
echo "$DOCKER_PUSH_REGISTRY_PASS" | docker login $DOCKER_PUSH_REGISTRY --username $DOCKER_PUSH_REGISTRY_USER --password-stdin
```

Push the latest build to dock
```shell
docker push registry.gitlab.com/tomshley/brands/usa/tomshleyllc/tech/www-tomshley-com-contact-service/www-tomshley-com-contact-service:latest
```

### Deploy The Service
```shell
kubectl delete namespace www-tomshley-com-contact-service-namespace
kubectl apply -f kubernetes/namespace.json
kubectl config set-context --current --namespace=www-tomshley-com-contact-service-namespace
envsubst < kubernetes/credentials-registry.yml | kubectl apply -f -
envsubst < kubernetes/connection-kafka.yml | kubectl apply -f -
envsubst < kubernetes/connection-yugabytedb.yml | kubectl apply -f -
kubectl apply -f kubernetes/rbac.yml
kubectl apply -f kubernetes/service.yml
kubectl apply -f kubernetes/deployment.yml
```

Tail logs
```shell
kubectl logs --follow -l app=www-tomshley-com-contact-service --namespace=www-tomshley-com-contact-service-namespace
```

Port forward the service
```shell
kubectl port-forward service/www-tomshley-com-contact-service-nlb 8181:80 -n www-tomshley-com-contact-service-namespace
```
```shell
kubectl port-forward service/www-tomshley-com-contact-service-k8service 8181:9900 -n www-tomshley-com-contact-service-namespace
kubectl port-forward pod/www-tomshley-com-contact-service-5fb58d8b5f-8wbls 8181:9900 -n www-tomshley-com-contact-service-namespace
```
```shell
grpcurl -d '{"name":"Tom Schena", "phone":"2156013948", "email":"tom@tom.com", "message":"hello world"}' -plaintext 127.0.0.1:8181 contact.ContactService.InboundContact
```
```shell
kubectl get pod/www-tomshley-com-contact-service-5fb58d8b5f-8wbls -n www-tomshley-com-contact-service-namespace --template='{{(index (index .spec.containers 0).ports 0).containerPort}}{{"\n"}}'
```
```shell
kubectl exec --stdin --tty www-tomshley-com-contact-service-7f8fbd4449-4whwm -- /bin/bash
curl api.ipify.org
wget -qO - https://api.ipify.org
```
