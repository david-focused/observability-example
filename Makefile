.PHONY: help setup-minikube build deploy all clean port-forward-jaeger port-forward-services port-forward-all stop-port-forwards restart-port-forwards check-port-forwards

# Default target
.DEFAULT_GOAL := help

# Help target - Always good to have it near the top for visibility
help:
	@echo "Available targets:"
	@echo "  setup-minikube         - Set up minikube with necessary addons"
	@echo "  build                  - Build all service images"
	@echo "  deploy                 - Deploy all services using Kustomize"
	@echo "  all                    - Build and deploy all services using Kustomize"
	@echo "  clean                  - Remove all deployed services"
	@echo "  port-forward-jaeger    - Forward Jaeger UI port to localhost"
	@echo "  port-forward-services  - Forward service ports to localhost"
	@echo "  port-forward-all       - Forward all ports (Jaeger UI and all services)"
	@echo "  stop-port-forwards     - Stop all running port-forward processes"
	@echo "  restart-port-forwards  - Restart all port-forward processes"
	@echo "  check-port-forwards    - List all running port-forward processes"

# Setup targets
setup-minikube:
	minikube start --memory=4096 --cpus=2
	minikube addons enable ingress
	minikube addons enable metrics-server
	@echo "Minikube setup complete. You may need to run 'eval \$$(nikube docker-env)' to use the Docker daemon inside minikube."
	@echo "You can also open the kubernetes dashboard via 'minikube dashboard'"

# Build and deployment targets
build:
	cd order-service && \
	make build && \
	cd ../shipping-service && \
	make build && \
	cd ../inventory-service && \
	make build

deploy:
	kubectl apply -k k8s/overlays/local

all: build deploy

clean:
	kubectl delete -k k8s/overlays/local --ignore-not-found || true
	# Clean up any remaining resources not managed by kustomize
	kubectl delete deployment --all --ignore-not-found || true
	kubectl delete service --all --ignore-not-found || true
	kubectl delete configmap --all --ignore-not-found || true

# Port forwarding targets
port-forward-jaeger:
	@echo "Port forwarding jaeger-ui to localhost:16686..."
	kubectl port-forward svc/jaeger-ui 16686:16686 > /dev/null 2>&1 &

port-forward-services:
	@echo "Port forwarding order-service to localhost:8080..."
	kubectl port-forward svc/order-service 8080:8080 > /dev/null 2>&1 &
	@echo "Port forwarding shipping-service to localhost:8081..."
	kubectl port-forward svc/shipping-service 8081:8080 > /dev/null 2>&1 &
	@echo "Port forwarding inventory-service to localhost:8082..."
	kubectl port-forward svc/inventory-service 8082:8080 > /dev/null 2>&1 &

port-forward-all: port-forward-jaeger port-forward-services
	@echo "All services and Jaeger UI have been port-forwarded"

stop-port-forwards:
	@echo "Stopping all kubectl port-forward processes..."
	@pkill -f "kubectl port-forward" || echo "No port-forward processes found"
	@echo "All port forwards have been stopped"

restart-port-forwards: stop-port-forwards port-forward-jaeger port-forward-services
	@echo "All port forwards have been restarted"

check-port-forwards:
	@echo "Checking active port forwards..."
	@ps aux | grep "[k]ubectl port-forward" || echo "No port-forwards active"
