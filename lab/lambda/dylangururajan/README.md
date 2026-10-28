# Lambda Lab — CoordinateNormalizerLambda

**Package:** `edu.calpoly.lambdalab.dylangururajan`  
**Class:** `CoordinateNormalizerLambda`  
**HTTP method:** `GET`  
**Route:** `/5100/coordinate-normalizer`  
**Public endpoint:** `https://api.javiergs.phd/5100/coordinate-normalizer`

## Problem

Convert pixel coordinates to normalized coordinates.

## Contract

**Input:** Query: x,y,width,height  
**Output:** normalized x and y

Return HTTP `200` for a valid request and HTTP `400` for invalid input. Keep business logic separate from the AWS/API Gateway adapter in `handleRequest()`.

## Your Work

Create:

- `src/main/java/edu/calpoly/lambdalab/dylangururajan/CoordinateNormalizerLambda.java`
- `src/test/java/edu/calpoly/lambdalab/dylangururajan/CoordinateNormalizerLambdaTest.java`

Use the instructor `TemperatureLambda` and its tests as the reference for API Gateway request/response handling. Implement the assigned behavior and write at least three meaningful JUnit tests.

Required test cases: **Center point; image boundary; invalid dimensions/input**.

## Test the Deployed Lambda

After your code passes GitHub Actions and is deployed:

Open in a browser:

`https://api.javiergs.phd/5100/coordinate-normalizer?x=960&y=540&width=1920&height=1080`

Your response must be JSON and follow the contract above.

## Pull Request

Create **one PR** for this lab. Do not create an Issue, Story, or Task.

PR title:

`Lambda Lab - CoordinateNormalizerLambda - <GitHubID>`

In the PR description include:

- Lambda: `CoordinateNormalizerLambda`
- Method and route: `GET /5100/coordinate-normalizer`
- Confirmation that JUnit tests pass
- Confirmation that `mvn clean package` succeeds
- The `api.javiergs.phd` endpoint tested
- The result of the deployed test

## A04 extension

The distributed-system extension, MQTT console instructions, and integration-evidence checklist are
documented in [Coordinate Normalizer A04](../../../docs/a04/dylangururajan-coordinate-normalizer.md).
