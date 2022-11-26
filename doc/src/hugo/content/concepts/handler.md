---
title: "Handlers"
draft: false
---

A *handler* is a very important definition in RIDDL because it is RIDDL's 
way of expressing both business logic and relationships between the various
components in a model. Handlers do that by specifying what should be done 
whenever a [message]({{< relref "message.md" >}}) of a particular type is
received by its parent definition. Handlers are composed as a set of
[on clauses]({{< relref "onclause.md" >}}) that connect a
[message]({{< relref "message.md" >}}) with an 
[example]{{< relref example.md >}}. The example specifies the business logic 
that should be executed upon receipt of the message. Because that business 
logic can send and publish further messages to other components, a 
relationship can be inferred between the component receiving the message 
sent and the component that contains the handler.

There are several kinds of handlers depending on the handler's containing
definition, as shown in this table:

| Occurs In   | Handler Focus                                                  |   
|-------------|----------------------------------------------------------------|
| Adaptor     | Translate messages between [contexts]({{< relref context.md>}} |
 | Application | Handling the messages received from the user                   |
| Context     | Implements API of the stateless context                        |
| Entity      | Handler to use on new entity before any morph action           |
| Processor   | Provide ETL logic for moving inputs to outputs                 |
| Projection  | Handle updates and queries on the projection                   |
| Repository  | Handle updates and queries on the repository                   |
| State       | Handle messages while entity is in that state                  |

The types of definition in which Handlers occur are known as the 
"active" definitions. More details are provided in the sections below. 

## Adaptor Handlers
Adaptor handlers provide the translation of messages from 
[context]({{< relref context.md >}}) to another.

## Application Handlers
Application handlers process the events generated by the Application's user 
to invoke business logic that typically results in sending further messages 
to the "back end" (typically a *gateway* context). This allows the 
application to be connected to the rest of the model. 

## Context Handlers
Context handlers imply a stateless API for the context, perhaps 
encapsulating the other things defined within the 
[context]({{< relref context.md >}}).

## Entity Handlers
Entity handlers specify the default or "catch all" handler for an entity.  
When the entity is new, or is not in a specific state, this default handler 
is used to process each message. If the message is not processed by the handler,
then the entity's processing for that message is null (message ignored).
When an entity is in a specified state, it processes messages defined by 
the handler within that state (see below).

## Projection Handler
Projections provide 

## State Handler
State handlers process messages while an entity is in that specific 
state, presumably with the intent of updating that state by generating 
events from commands.

## Occurs In
* [Adaptors]({{< relref "adaptor.md" >}})
* [Applications]({{< relref "application.md" >}})
* [Contexts]({{< relref "context.md" >}})
* [Entities]({{< relref "entity.md" >}}) 
* [Processors]({{< relref "processor.md" >}})
* [Projections]({{< relref "projection.md" >}})
* [Repositories]({{< relref "repository.md" >}})
* [State]({{< relref "state.md" >}})

## Contains
* [On Clauses]({{< relref "onclause.md" >}}) - a specification of how to 
  handle an event