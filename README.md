# maestro
Maestro is a container management tool for web applications that uses docker as its base container platform. 

# Architecture

[Diagram](architecture_v1.0.png) of architecture

The tool will provide the developer with admin tools to efficiently administrate the software, create a desired application model,  deploy containers, easily manage and monitor containers's execution at runtime and add external resources if necessary. 

Every container that will be deployed will be bind with a Container Service Request Broker (S.B. for short). The S.B. will handle container's interaction with the environment. More specifically it will inspect the container, register the services offered by the container to the service discovery mechanism, create/read/update/delete the container's configuration to the configuration store and query the service discovery mechanism for needed services. All the S.B.s are logically mapped to a Container Service Request Broker Layer.

Before deployment, the developer will be able to use custom annotations for every container describing necessary configuration options e.g. @HasWebservice will indicate that the deployed container is a webservice producer. All these dependencies will be injected to the S.B. before and at runtime, enabling an easily extensible and customizable mechanism while managing container dependencies dynamically.

For every container type there is a Service Provider. A Service Provider will support and implement all the necessary APIs and SPIs in order to provide services to the according container type. A Service Provider will communicate with Container Service Request Brokers. 

The Service Discovery and Configuration Store will help containers register and find services dynamically while manipulating the available configuration.

A Naming Service will handle the mapping of resources to names and will be stored to the Configuration Store.

#### Under heavy development!


