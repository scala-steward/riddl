---
title: "Domain"
draft: false
---

A domain is the top-most definitional level in RIDDL. We use the word 
*domain* in the sense of a *knowledge domain*; like an entire business, or 
some portion of it. It has nothing to do with Internet domain names. A domain
is an arbitrary boundary around some subset of concepts in the universe. As 
with Domain Driven Design, RIDDL uses the concept of a domain to group together
a set of related concepts.

Domains can recursively contain other nested domains so that a hierarchy of 
domains and subdomains is established.  Because of this, we can organize any
large, complex knowledge domain or field of study, into an
[hierarchical ontology](https://en.wikipedia.org/wiki/Ontology#Flat_vs_polycategorical_vs_hierarchical).

For example, if you were modelling the domain of *Two Wheeled Vehicles* you
might devise a domain hierarchy like this:
* Two Wheeled Vehicle
    - Motorized
        - Electric Bicycles
    * Gas Powered Motorcycles
  * UnMotorized
    * Bicycles
    * Human With Training Wheels

## Occurs In

* [Root]({{< relref "root.md" >}})

## Contains

Within a domain, you can define these things:

* [Authors]({{< relref "author.md" >}}) - who defined the domain
* [Contexts]({{< relref "context.md" >}}) - a precisely defined bounded context within the domain
* [Domains]({{< relref "domain.md" >}}) {{< icon "rotate-left" >}} - domains 
  can have nested domains (subdomains)
* [Includes]({{< relref "include.md" >}}) - inclusion of entity content from a
  file
* [Options]({{< relref "option.md" >}}) - optional declarations about a 
  definition
* [Plants]({{< relref "plant.md" >}}) - a data processing plant
* [Stories]({{< relref "story.md" >}}) - a story about external entities
  interacting with the domain
* [Terms]({{< relref "term.md" >}}) - definition of a term relevant to the
  domain
* [Types]({{< relref "type.md" >}}) - information definitions used throughout
  the domain 