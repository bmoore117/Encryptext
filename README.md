# Overview
This repo contains code for an Android app that sends and receives encrypted text messages using a combination of ECDSA and AES256.

When starting a conversation with a person, the app does a lookup on that person's phone number in its internal database, to see if it has a key for that person. If not, it prompts the user to begin a key exchange between them and who they would like to talk to. The other person will receive a notification that someone wants to exchange keys with them, which they can respond or deny. If a key was found for the phone number entered, users can transparently send and receive messages, without any further action.

In order to avoid dealing with 7-bit character encoding issues, which is the default encoding for SMS messages, this app uses Android's sendDataMessage functionality, which sends a standard 8-bit character. As the 8-bit SMS format does not come with the standard SMS protocol features for handling message sent confirmations or multipart messages, a small TCP-like protocol is implemented in the app.


# Code
Broadly speaking, the app breaks down into UI, and backing services. The main screen in the UI is the `Conversation` activity, and sending and receiving messages is handled by the two services `SenderSvc` and `ReceiverSvc`.
