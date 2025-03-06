IMAGE_NAME=inventory-service
MINIKUBE_ENV := eval $$(minikube docker-env)

.PHONY: clean build

clean:
	@docker rmi inventory-service:latest

build:
	@$(MINIKUBE_ENV) && docker build -t $(IMAGE_NAME):latest .

all: clean build 