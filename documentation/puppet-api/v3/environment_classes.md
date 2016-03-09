---
layout: default
title: "Puppet Server: Puppet API: Environment Classes"
canonical: "/puppetserver/latest/puppet/v3/environment_classes.html"
---

[auth.conf]: /puppetserver/latest/config_file_auth.html

The environment classes API serves as a replacement for the functionality
available in the Puppet
[resource type API](/puppet/latest/reference/http_api/http_resource_type.html)
for classes.  Key differences between the two APIs include:

* The environment classes API only covers "classes" whereas the resource
  type API covers classes, nodes, and defined types.

* Queries to the resource type API utilize cached class information per
  the configuration of the
  [environment_timeout](/puppet/latest/reference/config_file_environment.html#environmenttimeout)
  setting for the corresponding environment.  The environment classes API
  does not utilize the "environment_timeout" with respect to the data that it 
  caches.  Instead, only when the `environment-class-cache-enabled` setting in
  the `jruby-puppet` configuration section is set to `true`, the environment
  classes API uses HTTP Etags to represent specific versions of the class
  information and the Puppet Server
  [environment-cache API](./admin-api/v1/environment-cache.html) as an
  explicit mechanism for marking an Etag as expired.  See the
  [Headers and Caching Behavior](#headers-and-caching-behavior) section for more
  information about caching and invalidation of entries.
  
* The environment classes API includes a "type", if defined for a class 
  parameter.  For example, if the class parameter were defined as
  `String $some_str`, the "type" parameter would hold a value of "String".

* For values that can be presented in pure JSON, the environment classes API 
  provides a "default_literal" form of a class parameter's default value.  For
  example, if an "Integer" type class parameter were defined in the manifest as
  having a default value of "3", the "default_literal" element for the
  parameter will contain a JSON Number type of 3.

* The environment classes API does not provide a way to filter the list of
  classes returned via use of a search string.  The environment classes API
  returns information for all classes found within an environment's manifest
  files.

* Unlike the resource type API under Puppet 4, the environment classes API
  does include the filename in which each class was found.  The resource 
  type API under Puppet 3 does include the filename but the resource type 
  API under Puppet 4 does not.

* The environment classes API does not include the line number at which a
  class is found in the file.

* Unlike the resource types API under Puppet 3, the environment classes API 
  does not include any doc strings for a class entry.  Note that doc strings 
  are also not returned for class entries in the Puppet 4 resource type API.
  
* The environment classes API returns a file entry for manifests that exist 
  in the environment but in which no classes were found.  The resource type
  API omits entries for files which did not contain any classes.

* The Content-Type in the response to an environment classes API query is 
  "application/json" whereas the resource types API uses a Content-Type of 
  "text/pson".
  
* If an error is encountered when parsing a manifest, the resource types API 
  omits information from the manifest entirely - although it will, 
  assuming none of the parsing errors were found in one of the files associated
  with the environment's
  [`manifest` setting](/puppet/latest/reference/config_file_environment.html#manifest),
  include class information from other manifests that could successfully be
  parsed.  In the case that one or more classes is returned but errors were 
  encountered parsing other manifests, the response from the resource types API 
  call does not include any explicit indication that a parsing error has been
  encountered.  The environment classes API will include information for every
  class that can successfully be parsed.  For any errors which occur when 
  parsing individual manifest files, an entry for the corresponding manifest 
  file is included, along with an "error" with a detail string about the failure
  encountered during parsing.

## `GET /puppet/v3/environment_classes?environment=:environment`

Making a request with no query parameters is currently not supported and will
provide an HTTP 400 (Bad Request) response.

### Supported HTTP Methods

GET

### Supported Formats

JSON

### Query Parameters

One parameter should be provided to the GET:

* `environment`: Only the classes and parameter information pertaining to the
  specified environment will be returned for the call.

### Responses

#### Get With Results

```
GET /puppet/v3/environment_classes?environment=env

HTTP/1.1 200 OK
Etag: b02ede6ecc432b134217a1cc681c406288ef9224
Content-Type: text/json

{
  "files": [
    {
      "path": "/etc/puppetlabs/code/environments/env/manifests/site.pp",
      "classes": []
    },
    {
      "path": "/etc/puppetlabs/code/environments/env/modules/mymodule/manifests/init.pp",
      "classes": [
        {
          "name": "mymodule",
          "params": [
            {
              "default_literal": "this is a string",
              "default_source": "\"this is a string\"",
              "name": "a_string",
              "type": "String"
            },
            {
              "default_literal": 3,
              "default_source": "3",
              "name": "an_integer",
              "type": "Integer"
            }
          ]
        }
      ]
    },
    {
      "error": "Syntax error at '=>' at /etc/puppetlabs/code/environments/env/modules/mymodule/manifests/other.pp:20:19",
      "path": "/etc/puppetlabs/code/environments/env/modules/mymodule/manifests/other.pp"
    }
  ],
  "name": "env"
}
```

#### Get With Etag Roundtripped from Previous Get
 
If the Etag value returned from the previous request for information from an
environment is sent to the server in a follow-up request and the underlying
environment cache has not been invalidated, an HTTP 304 (Not Modified) response
will be returned.  See the
[Headers and Caching Behavior](#headers-and-caching-behavior) section for more
information about caching and invalidation of entries.

```
GET /puppet/v3/environment_classes?environment=env
If-None-Match: b02ede6ecc432b134217a1cc681c406288ef9224

HTTP/1.1 304 Not Modified
Etag: b02ede6ecc432b134217a1cc681c406288ef9224
```

If the environment cache has been updated from what was used to calculate the
original Etag, the server will return a response with the full set of
environment class information:

```
GET /puppet/v3/environment_classes?environment=env
If-None-Match: b02ede6ecc432b134217a1cc681c406288ef9224

HTTP/1.1 200 OK
Etag: 2f4f83096265b9741c5304b3055f866df0336762
Content-Type: text/json

{
  "files": [
    {
      "path": "/etc/puppetlabs/code/environments/env/manifests/site.pp",
      "classes": []
    },
    {
      "path": "/etc/puppetlabs/code/environments/env/modules/mymodule/manifests/init.pp",
      "classes": [
        {
          "name": "mymodule",
          "params": [
            {
              "default_literal": "this is a string",
              "default_source": "\"this is a string\"",
              "name": "a_string",
              "type": "String"
            },
            {
              "default_literal": 3,
              "default_source": "3",
              "name": "an_integer",
              "type": "Integer"
            },
            {
              "default_literal": {
                "one": "foo",
                "two": "hello"
              },
              "default_source": "{ \"one\" => \"foo\", \"two\" => \"hello\" }",
              "name": "a_hash",
              "type": "Hash"
            }
          ]
        }
      ]
    }
  ],
  "name": "env"
}
```

#### Environment does not exist

A request made with an environment parameter which does not correspond to the
name of a directory environment on the server results in an HTTP 404 (Not
Found) error:

```
GET /puppet/v3/environment_classes?environment=doesnotexist

HTTP/1.1 404 Not Found

Could not find environment 'doesnotexist'
```

#### No environment given

```
GET /puppet/v3/environment_classes

HTTP/1.1 400 Bad Request

An environment parameter must be specified.
```

Note that this endpoint may support the ability to provide a success response
with valid data in a future release.

#### Environment parameter specified with no value

```
GET /puppet/v3/environment_classes?environment=

HTTP/1.1 400 Bad Request

The environment must be purely alphanumeric, not ''
```

#### Environment does not include only alphanumeric characters

A request made with an environment parameter which includes at least one
character which is not among the following - A-Z, a-z, 0-9, or _ (underscore) -
results in an HTTP 400 (Bad Request) error:

```
GET /puppet/v3/environment_classes?environment=bog|us

HTTP/1.1 400 Bad Request

The environment must be purely alphanumeric, not 'bog|us'
```

### Schema

An environment classes response body conforms to
[the environment classes schema](./environment_classes.json).
 
### Headers and Caching Behavior

If the `environment-class-cache-enabled` setting in the `jruby-puppet`
configuration section is set to `true`, the `environment_classes` API can
make use of caching for the response data.  This can provide a significant
performance benefit and reduction in the amount of data that needs to be
provided in a response when the underlying Puppet code on disk remains
unchanged from one request to the next.  Use of the cache does, however,
require that cache entries are invalidated after Puppet code has been
updated.  To avoid having to invalidate cache entries, the
`environment-class-cache-enabled` setting can be omitted from configuration 
or explicitly set to `false`.  In this case, manifests will be re-discovered
from disk and re-parsed for every incoming request.  This can involve
significantly greater overhead in performance and bandwidth for repeated 
requests where the underlying Puppet code does not change much.  This 
approach does, however, ensure that the latest available data is returned 
for every request made.  The rest of the content in this section pertains to the
behaviors to keep in mind when the cache setting is enabled.

When the `environment-class-cache-enabled` setting is set to `true`, the 
response to a query to the `environment_classes`  endpoint includes an HTTP
[Etag](https://tools.ietf.org/html/rfc7232#section-2.3) header.  The value 
for the Etag header is a hash which represents the state of the latest class
information available for the requested environment.  For example:

```
ETag: 31d64b8038258202b4f5eb508d7dab79c46327bb
```

A client may (but is not required to) provide the Etag value back to the server
in a subsequent `environment_classes` request.  The client would provide the
tag value as the value for an
[If-None-Match](https://tools.ietf.org/html/rfc7232#section-3.2)
HTTP header:

```
If-None-Match: 31d64b8038258202b4f5eb508d7dab79c46327bb
```

If the latest state of code available on the server matches that of the value
in the `If-None-Match` header, the server will provide an HTTP 304 (Not
Modified) response and no corresponding response body.  If the server has later
code available than what is captured by the `If-None-Match` header value or
no `If-None-Match` header is provided in the request, the server will re-parse
code from manifest files on disk.  Assuming the resulting payload were different
than in a previous request, the server would provide a different Etag value
along new class information in the response payload.

Note that the server may, in cases where the client has sent an
`Accept-Encoding: gzip` HTTP header for the request and the server has chosen
to provide a gzip-encoded response body, append the characters "--gzip" to
the end of the Etag.  For example, the HTTP response headers could include:

```
Content-Encoding: gzip
ETag: e84bbce5482243b3eb3a190e5c90e535cf4f20de--gzip
```

The server will accept both forms of an Etag - with or without the trailing
"--gzip" characters - as being the same when validating the value in a
request's `If-None-Match` header against its cache.

It is best, however, for clients to utilize the Etag in an opaque manner, not
parsing its content.  A client wanting to get an HTTP 304 (Not Modified)
response if the cache has not been updated since the prior request should
take the exact value returned in the `Etag` header from one request and provide
that back to the server in an `If-None-Match` header in a subsequent request
for the class information for an environment.

---

After Puppet code (manifests) on disk in an environment are updated, the cache 
entries that the server holds for class information will need to be cleared. 
This will allow the server to re-parse the latest manifest code on disk and 
reflect any changes to the class information in later queries to the 
environment_classes endpoint.  The following actions will clear cache entries on
the server:

1. Calling the
   [environment-cache API](latest/admin-api/v1/environment-cache.html) endpoint.
   
   For best performance, it is best to call this endpoint with a query 
   parameter for the specific `environment` whose cache should be flushed.
   
2. Performing a `commit` through the
   [File Sync API](/pe/latest/cmgmt_filesync_api.html#post-file-syncv1commit)
   endpoint.
   
   Note that the "File Sync" feature is only available in Puppet Enterprise. 
   When a commit is performed through the "File Sync" API, it is unnecessary 
   to also invoke the `environment-cache` API to flush the environment's 
   cache.  The environment's cache is implicitly flushed as part of the sync 
   of new commits to a master using the File Sync feature.

3. Restarting Puppet Server.

   The cache for every environment is held in memory for the Puppet Server 
   process and so is effectively flushed whenever Puppet Server is restarted.

### Authorization

URI authorization for requests made to the environment classes API will always
be performed by the "new" Trapperkeeper-based auth.conf feature in Puppet
Server.  This is different than most other master service-based endpoints,
for which the authorization mechanism used ("new" auth.conf vs. "legacy"
auth.conf) is controlled by the `use-legacy-auth-conf` setting in the
`jruby-puppet` configuration section.  The value of the `use-legacy-auth-conf`
setting is ignored for the environment classes API and so the "legacy"
auth.conf mechanism is never used when authorizing requests made to the
environment classes API.  For more information about authorization, see
the [`auth.conf` documentation][auth.conf].