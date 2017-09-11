![Zenodo](http://about.zenodo.org/static/img/logos/zenodo-gradient-square.svg)
# ZenodoAddon (Backend)
> A Keyword Recommendation and Anomaly Detection Service for Zenodo, an open-source document repository funded and powered by [CERN](http://cern.ch)

## Tech Stack
* 🐍 **Scala 2.12** - JVM language with practically absolute interopability with Java
* 🗒️ **Postgres** - Full-text search capability for finding origin vertices
* 🐛 **Redis** - Caching computation results and serving them on-demand
* 🐋 **Docker** - Containerization and easy dev/prod setup
* 🎓 **Stanford CoreNLP** - POS/NER sample for Graph Transformer

## Getting Started
* Download SBT at https://scala-lang.org/download/
* Download Docker and Docker-Compose (Community Edition)
  * https://docs.docker.com/engine/installation/
  * https://docs.docker.com/compose/install/#install-compose (if Docker-Compose wasn't already installed)
  * Check that both Docker and Docker-Compose is installed
    * Run `docker -v`
    * Run `docker-compose -v`
* `git clone` this project 
* `cd` into the project's root
* Spin up the Docker containers with `docker-compose up -d`
  * Note: The Postgres container must be prefilled with tsvectors (the keyword_raw table must be filled with prepopulated data). The Zenodo-Filescript script should be run with the parameters --fulltext_db_username, --fulltext_db_password, --fulltext_db_name, --fulltext_db_host, --fulltext_db_port set to the same database configuration so that it fills up this specific Postgres Docker container (the script will automatically prepopulate data based on data it picks up from the source db, in this case, Zenodo's DB servers)
* While still being in the same project base directory, copy `.env_sample` to a new `.env` file and make sure to fill in all environment variables as specified in the file
* While still being in the same project base directory, run `env $(tr "\\n" " " < .env) sbt run`
  * This turns the .env file into actual environment variables that our program can see
* `sbt run` 
  * This command compiles the program and runs it.
  * With the port number specified as an environment variable PORT through .env file, you can now make POST requests to localhost:[SPECIFIED_PORT_NUMBER]/recommendation

You can now make requests to the microservice!


## DB Schema
The Postgres database (that is available through the provided Docker container) sports the following data schema (automatically created) - database tables followed by the database column types for the full-text search capability:
* **keyword_raw** - id:uuid, keyword:text, keyword_vector:tsvector

Also, 2 functions are automatically created on DB bootstrap - `create_keyword(keyword_text)` and `search_keyword_matches(keyword_text)`. The former stores the provided keyword_text into the keyword_raw table. The latter searches the current keyword for possible close matches in the database.


## API Endpoint
* POST _/recommendation/_
  * Request: `json` - must be provided as the body of the request
       ```javascript
        {
        	"keyword": ["ghost", "electricity"] // Keyword tags a user gave
        	"ranker": "distance" // Could be distance, ppr, pprMean
        	"vertexFinder": "fulltext" // Could be fulltext, plain
        	"count": 20,
        	"addons": ["cache"] // Only available addon is cache for the moment. (optional)
        }
        ```

  * Response: `json` - emitted by the microservice
       ```javascript
        {
        	"success": true // if >= 400, false
        	"message": "result message"
        	"data": {
        	    "addons": {} // Dict mapping of addon names to execution metadata
        	    "results": [] // Actual list of keyword recommendations
        	}
        ```
