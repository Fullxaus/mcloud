# Kubernetes Deployment — mcloud_11

## Установка Minikube

```bash
minikube version
```

```
minikube version: v1.38.1
```

## Установка kubectl

```bash
kubectl version --client
```

```
Client Version: v1.34.1
```

## Запуск кластера

```bash
minikube start --memory=4096 --cpus=2
```

```bash
kubectl get nodes
```

```
NAME       STATUS   ROLES           AGE   VERSION
minikube   Ready    control-plane   28h   v1.35.1
```

## Подготовка Docker образа

Используется `WebApp.java` из урока MCLOUD-7 — простой HTTP-сервер с эндпоинтами `/` и `/health`.

```bash
# Сборка образа с оптимизированным multi-stage Dockerfile
docker build -t mcloud-webapp:latest -f docker/mcloud_7/Dockerfile.webapp.optimized .

# Загрузка образа в Minikube
minikube image load mcloud-webapp:latest
```

## Kubernetes манифесты

### pod.yaml — простой Pod

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: webapp-pod
  labels:
    app: webapp
spec:
  containers:
  - name: webapp
    image: mcloud-webapp:latest
    imagePullPolicy: Never
    ports:
    - containerPort: 8080
```

### deployment.yaml — Deployment с 3 репликами

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: webapp-deployment
spec:
  replicas: 3
  selector:
    matchLabels:
      app: webapp
  template:
    metadata:
      labels:
        app: webapp
    spec:
      containers:
      - name: webapp
        image: mcloud-webapp:latest
        imagePullPolicy: Never
        ports:
        - containerPort: 8080
```

### service.yaml — Service типа NodePort

```yaml
apiVersion: v1
kind: Service
metadata:
  name: webapp-service
spec:
  selector:
    app: webapp
  ports:
  - port: 80
    targetPort: 8080
  type: NodePort
```

## Применение манифестов

```bash
kubectl apply -f k8s/mcloud_11/
```

```
deployment.apps/webapp-deployment created
pod/webapp-pod created
service/webapp-service created
```

## Проверка статуса

```bash
kubectl get all
```

```
NAME                                     READY   STATUS    RESTARTS   AGE
pod/webapp-deployment-7f8fcd7498-6889p   1/1     Running   0          3m
pod/webapp-deployment-7f8fcd7498-8grps   1/1     Running   0          3m
pod/webapp-deployment-7f8fcd7498-h74zp   1/1     Running   0          3m
pod/webapp-pod                           1/1     Running   0          3m

NAME                     TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)        AGE
service/kubernetes       ClusterIP   10.96.0.1       <none>        443/TCP        28h
service/webapp-service   NodePort    10.99.238.100   <none>        80:31413/TCP   3m

NAME                                READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/webapp-deployment   3/3     3            3           3m
```

Все 4 пода в статусе **Running** (1 standalone pod + 3 реплики из Deployment).

## Доступ к приложению

```bash
kubectl port-forward service/webapp-service 8081:80
```

Приложение доступно по адресу: http://localhost:8081

### Проверка работоспособности

```
GET http://localhost:8081/       -> Welcome! Visitor #1
GET http://localhost:8081/health -> {"status":"UP"}
```
