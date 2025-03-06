This repo sets up a few services in a microservices architecture and uses OpenTelemetry to instrument them, providing traces that can then be visualized in Jaeger.

## Architecture

The system consists of the following services (just for example purposes):
- **Order Service**: Handles order creation and management
- **Shipping Service**: Processes shipping requests for orders
- **Inventory Service**: Manages product inventory and availability
- **OpenTelemetry Collector**: Collects and processes telemetry data
- **Jaeger**: Visualization and analysis of distributed traces

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/)
- [Minikube](https://minikube.sigs.k8s.io/docs/start/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
- [Kustomize](https://kubectl.docs.kubernetes.io/installation/kustomize/) (included with recent kubectl versions)

## Getting Started

### 1. Start Minikube

```bash
make setup-minikube
```

This will start Minikube and enable necessary addons like ingress and metrics-server.

I also recommend following this up with:
```bash
eval $(minikube docker-env)
```

to ensure that docker commands are run in the minikube cluster.

### 2. Build and Deploy Services

The repository provides a Makefile for easy building and deployment:

```bash
# Build all services
make build

# Deploy all services using Kustomize
make deploy

# Or, build and deploy in one command
make all
```

We're using Kustomize to manage Kubernetes manifests

### 3. Accessing the Services

Once deployed, you can forward the service ports to your local machine:

```bash
# Forward all ports (Jaeger UI and all services)
make port-forward-all
```

Access Order Service at http://localhost:8080

Access Shipping Service at http://localhost:8081

Access Inventory Service at http://localhost:8082

Access Jaeger UI at http://localhost:16686


### 4. Testing the System

The repository includes a test script to demonstrate the distributed tracing capabilities across all services:

```bash
# Run the test script
./test-observability.sh
```

This script provides an interactive menu with the following test scenarios:

1. **Happy Path Test**: Creates a successful order that flows through all services (order → inventory → shipping)
2. **Service Latency Test**: Demonstrates how delays in the inventory service appear in traces
3. **Error Path Test**: Shows how errors in the inventory service propagate through the system
4. **Multi-Scenario Test**: Runs all patterns for side-by-side comparison in Jaeger

Each test generates distributed traces that can be viewed in the Jaeger UI, showing the complete request flow across all microservices.


### Cleaning Up

To remove all deployed services:

```bash
make clean
``` 

## Troubleshooting

### Common Issues

- **Images not found**: Ensure you've built the images with the correct tags and are using the Minikube Docker daemon:
  ```bash
  eval $(minikube docker-env)
  ```

- **Services not connecting**: Check the service names and ports in the Kubernetes manifests

- **No traces appearing**: Verify the OpenTelemetry environment variables are correctly set in the deployments
