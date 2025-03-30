# MDD Schema

When you have data types, you have to have schema's to describe them! This package contains a schema language to describe data types in [MDD](https://github.com/celerex/mdd-core) format.

## Types

A data type is a definition of what the data should look like. Overall there are two different types:

- **scalar**: the data contains exactly one atomic value
- **complex**: the data contains other data which in turn can be more complex data or scalar values

### Scalar

MDD only recognizes one scalar type by default: `string`. It is not defined anywhere, it simply exists as _the_ scalar type.

Every other scalar type is a derivative of the string type and typically adds restrictions on what forms it can take.
For interoperability with major programming languages, a basic set of scalar types is provided that should map to primitive types in their respective languages. 

The schema parser will automatically add this basic set though this can be turned off.



## Namespaces

Each type is defined in a namespace. 

Each definition file has exactly one target namespace where new types will be created. 
Each namespace is meant to be globally unique and SHOULD take the form a reversed domain name that you possess.
If you are working on internal schema's that are never meant to go public, use the "local" TLD.


```mdd
+ namespace: local.application
```

If no namespace is defined, the global namespace is used. You SHOULD NOT use the global namespace unless you have a _really_ good reason to.

### Imports

You can import other items by defining a list of import statements. The import is configured as metadata so will not collide with a type that you want to call "import".

```mdd
+ import: 
	- http\://example.com/first
	- http\://example.com/second
```

