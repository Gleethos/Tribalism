import SockJS from "sockjs-client"
import {Stomp} from "@stomp/stompjs"

var stompClient = null;

function setConnected(connected) {
    document.getElementById("connect").disabled = connected;
    document.getElementById("disconnect").disabled = !connected;
    if (connected) {
        document.getElementById("conversation").style.display = "block";
    }
    else {
        document.getElementById("conversation").style.display = "none";
    }
}

export function connect() {
    var socket = new SockJS('/gs-guide-websocket');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        setConnected(true);
        console.log('Connected: ' + frame);
        stompClient.subscribe('/topic/greetings', function (greeting) {
            showGreeting(JSON.parse(greeting.body).content);
        });
    });
}

export function disconnect() {
    if (stompClient !== null) {
        stompClient.disconnect();
    }
    setConnected(false);
    console.log("Disconnected");
}

export function sendName() {
    // We get the name from the input field (id == "name")
    const name = document.getElementById("name").value;
    stompClient.send("/app/hello", {}, JSON.stringify({'name': name}));
}

function showGreeting(message) {
    // We get the greetings div (id == "greetings") and append the message:
    const greetings = document.getElementById("greetings");
    const tr = document.createElement("tr");
    const td = document.createElement("td");
    td.appendChild(document.createTextNode(message));
    tr.appendChild(td);
    greetings.appendChild(tr);
}

//$(function () {
//    $("form").on('submit', function (e) {
//        e.preventDefault();
//    });
//    $( "#connect" ).click(function() { connect(); });
//    $( "#disconnect" ).click(function() { disconnect(); });
//    $( "#send" ).click(function() { sendName(); });
//});