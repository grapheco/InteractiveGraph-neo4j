InteractiveGraphProtocol(IGP) defines a set of graph interaction interfaces via HTTP protocol.

An IGP request is a HTTP GET request with certain command, like:

```
graph?command=<command>&arg1=...&arg2=...&...
```

An IGP response is a HTTP response, mostly in JSON format.

A request-response pair is shown here as an example:

```
telnet localhost 9999
GET /graphserver/graph?command=init HTTP/1.0
{
  "product": {
    "name": "GraphBrowser",
    "version": "0.0.1"
  },
  "canvasWidth": 3000,
  "canvasHeight": 2000,
  "gridSize": 100,
  "numberOfNodes": 500,
  "hasImages": true,
  "hasDegrees": true,
  "hasXY": false
}
```

######
