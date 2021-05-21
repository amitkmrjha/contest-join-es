## Running the sample code

1. Start a local PostgresSQL server on default port 5432 and a Kafka broker on port 9092. The included `docker-compose.yml` starts everything required for running locally.

    ```shell
    docker-compose up -d

    # creates the tables needed for Akka Persistence
    # as well as the offset store table for Akka Projection
    docker exec -i contest-join-service_postgres-db_1 psql -U contest-join -t < ddl-scripts/create_tables.sql
    
    # creates the user defined projection table.
    docker exec -i contest-join-service_postgres-db_1 psql -U contest-join -t < ddl-scripts/create_user_tables.sql
    ```

2. Start a first node:

    ```shell
    sbt -Dconfig.resource=local1.conf run
    ```

3. (Optional) Start another node with different ports:

    ```shell
    sbt -Dconfig.resource=local2.conf run
    ```

4. (Optional) More can be started:

    ```shell
    sbt -Dconfig.resource=local3.conf run
    ```

5. Check for service readiness

    ```shell
    curl http://localhost:9101/ready
    ```

6. Try it with [grpcurl](https://github.com/fullstorydev/grpcurl):

    ```shell
    # add user to contest
   grpcurl -d '{"contestJoinRequest":{"contestId":"MegaContest123", "userId":"amit1", "joinMetaData":"kohlidecider"},"contestJoinRequest":{"contestId":"MegaContest123", "userId":"amit2", "joinMetaData":"kohlidecider1"}}' -plaintext 127.0.0.1:8101 contestjoin.ContestJoinService.JoinContest
   
   # get contest state by entity id
   grpcurl -d '{"contestId":"MegaContest123"}' -plaintext 127.0.0.1:8101 contestjoin.ContestJoinService.GetContestJoin
   
   # Query join by Contest id
   grpcurl -d '{"contestId":"MegaContest123"}' -plaintext 127.0.0.1:8101 contestjoin.ContestJoinService.GetJoinByContest
   
   # Query join by User id
   grpcurl -d '{"userId":"amit1"}' -plaintext 127.0.0.1:8101 contestjoin.ContestJoinService.GetJoinByUser
   
   # Query all join
   grpcurl  -plaintext 127.0.0.1:8101 contestjoin.ContestJoinService.GetAllJoin
    ```

    or same `grpcurl` commands to port 8102 to reach node 2.

7. Shut down supporting docker

    ```shell
    docker-compose down
    ```