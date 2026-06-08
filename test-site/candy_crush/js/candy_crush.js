var board = [];
var rows = 8;
var cols = 8;
var score = 0;

var colors = [
    "red",
    "blue",
    "green",
    "yellow",
    "purple",
    "orange"
];

var dragStart = null;

function startGame(){

    document.getElementById("playerName").value="Player1";

    createBoard();

    setInterval(function(){
        crushMatches();
    },300);
}

function createBoard(){

    let gameBoard = document.getElementById("gameBoard");

    gameBoard.innerHTML="";

    board=[];

    for(let r=0;r<rows;r++){

        board[r]=[];

        for(let c=0;c<cols;c++){

            let color = randomColor();

            board[r][c]=color;

            let candy = document.createElement("div");

            candy.className="candy";
            candy.id=r+"-"+c;
            candy.draggable=true;
            candy.style.background=color;

            candy.addEventListener("dragstart",dragStartFn);
            candy.addEventListener("dragover",dragOverFn);
            candy.addEventListener("drop",dropFn);

            gameBoard.appendChild(candy);
        }
    }
}

function randomColor(){
    return colors[Math.floor(Math.random()*colors.length)];
}

function dragStartFn(){
    dragStart=this;
}

function dragOverFn(e){
    e.preventDefault();
}

function dropFn(){

    if(!dragStart || dragStart===this){
        return;
    }

    let color1 = dragStart.style.background;
    let color2 = this.style.background;

    dragStart.style.background=color2;
    this.style.background=color1;
}

function crushMatches(){

    let candies=document.querySelectorAll(".candy");

    for(let r=0;r<rows;r++){

        for(let c=0;c<cols-2;c++){

            let idx1=r*cols+c;
            let idx2=r*cols+c+1;
            let idx3=r*cols+c+2;

            let color1=candies[idx1].style.background;

            if(
                color1!=="" &&
                color1===candies[idx2].style.background &&
                color1===candies[idx3].style.background
            ){

                candies[idx1].style.background="";
                candies[idx2].style.background="";
                candies[idx3].style.background="";

                increaseScore(30);
            }
        }
    }

    for(let c=0;c<cols;c++){

        for(let r=0;r<rows-2;r++){

            let idx1=r*cols+c;
            let idx2=(r+1)*cols+c;
            let idx3=(r+2)*cols+c;

            let color1=candies[idx1].style.background;

            if(
                color1!=="" &&
                color1===candies[idx2].style.background &&
                color1===candies[idx3].style.background
            ){

                candies[idx1].style.background="";
                candies[idx2].style.background="";
                candies[idx3].style.background="";

                increaseScore(30);
            }
        }
    }

    refillBoard();
}

function refillBoard(){

    let candies=document.querySelectorAll(".candy");

    candies.forEach(function(candy){

        if(candy.style.background===""){

            candy.style.background=randomColor();
        }
    });
}

function increaseScore(points){

    score += points;

    document.getElementById("score").innerHTML=score;
}

function playerBlur(){

    let name=document.getElementById("playerName").value;

    if(name.trim()===""){

        alert("Player name cannot be empty");
    }
}

function levelChanged(){

    let level=document.getElementById("level").value;

    alert("Level changed to "+level);
}

function hoverMessage(){

    document.getElementById("message").innerHTML=
        "Swap candies to make 3 matches!";
}

function clearMessage(){

    document.getElementById("message").innerHTML="";
}

document.getElementById("csp_auto_c4b268a3-dfed-3931-af3b-b3f3313d1df2")
        .addEventListener("load", function () {
            
        });

window.addEventListener("load", function () {
            startGame()
        });

document.getElementById("playerName")
        .addEventListener("blur", function () {
            playerBlur()
        });

document.getElementById("level")
        .addEventListener("change", function () {
            levelChanged()
        });

document.getElementById("gameBoard")
        .addEventListener("mouseover", function () {
            hoverMessage()
        });

document.getElementById("gameBoard")
        .addEventListener("mouseout", function () {
            clearMessage()
        });
