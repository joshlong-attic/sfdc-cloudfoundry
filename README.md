# SFDC on CloudFoundry 

This web application is the starting point on your journey. You login to SFDC here, and the application sends a message over RMQ that is picked up by an agent running in another process. the contents of the message are the SFDC key, secret, and access token required to rehydrate the SFDC API _without_ the use of any web facing screen in a process running on another node. The message that triggers the job will assign the whole process a unique correlation ID. The correllation ID could be something as simple as a UUID or the current time. This correllation ID will be used in subsequent work to track the results of a given *run*. 

There, the first agent to handle the work reads and stores all the contacts and leads in the database and then triggers the enrichment process. These enrichment agents iterate through every record and add extra data where possible, including the gravatar URL, the GitHub APIs, LinkedIn users, Twitter users, etc. 

Finally, once all of these forked enrichment processes are finished, they join up and a reply message is sent back to to the user waiting in the web application for more information to appear. Or perhaps each job sends back a reply as it finishes, letting the UI update dynamically. Either way, the client will update its view to reflect all the now enriched data stored in the database.

