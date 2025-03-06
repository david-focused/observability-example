IMAGE_NAME=order-service
MINIKUBE_ENV := eval $(minikube docker-env)

.PHONY: clean build

clean:
	@docker rmi order-service:latest

build:
	@$(MINIKUBE_ENV) && docker build -t $(IMAGE_NAME):latest .

all: clean build