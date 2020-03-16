# Credit Scoring Service

## Design

The application has been written using HTTP4S and Tagless Final.

The application roughly follows a DDD/Hexagonal Architecture.

The `domain` package contains Algebraic Data Types (ADTs) modelling the problem in an ideal fashion that doesn't make any
concessions to the REST API of the service or the REST API of upstream services that it integrates with. It doesn't make
use of any libraries. It could be used by any application that wanted to provide the same services. If the application 
had business logic I would add it here.

The `infrastructure` package contains code for integrating with external systems, namely being called on the REST api
or calling out to other REST apis. It depends on the `domain` package where appropriate (ie by implementing or requiring
access to the `CreditCardRecommendationService` service and its ADTs). 

The REST API has been further broken down into an API (for HTTP4S routes) and a Service (for intermediating with the
Domain Service). I did this because I find it makes the API/routing code more readable to be purely concerned with
matching http requests and choosing the http response. In more complex code bases I might add separate unit tests for 
these two classes. In this case I felt a single Integration Test suite was sufficient.

There is a lot of deliberate repetition in this codebase. Although the different sub-domains are superficially similar
trying to reuse code between them would likely cause confusion and mistakes (eg mistaking the scale used by different
services).

This application is missing logging and monitoring. Tagless Final makes logging quite pleasant as you can capture it as
an effect. Monitoring works well in a hexagonal architecture because you can just add a trait to the domain package
that infrastructure classes depend on and then only mix in the implementation when run at production (or a fake version
for tests).

## Deployment

I would probably deploy this service as a Docker Container (using the sbt native packager plugin) into AWS Fargate.
It's a relatively simple turnkey infrastructure that suits long running applications.
