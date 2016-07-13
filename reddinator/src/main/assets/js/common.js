
function LightenDarkenColor(col,amt) {
    col = parseInt(col.substring(1, 7),16);
    return (((col & 0x0000FF) + amt) | ((((col>> 8) & 0x00FF) + amt) << 8) | (((col >> 16) + amt) << 16)).toString(16);
}

function setTheme(themeColors){
    $("body").css("background-color", themeColors["background_color"]);
    $("#loading_view, .reply_expand, .more_box").css("color", themeColors["load_text"]);
    $(".border, .sub-border").css('border-color', themeColors['comments_border']);
    $(".comment_text").css("color", themeColors["comment_text"]?themeColors["comment_text"]:themeColors["headline_text"]);
    $(".comment_user").css("color", themeColors["source_text"]);
    $(".message_type, .message_subject").css("color", themeColors["load_text"]);
    $(".fa-star").css("color", themeColors["votes_icon"]);
    $(".comment_score").css("color", themeColors["votes_text"]);
    $(".fa-comment").css("color", themeColors["comments_icon"]);
    $(".comment_reply_count").css("color", themeColors["comments_text"]);
    $("button").css("background-color", themeColors["header_color"]);
    $("body").show();
}

function addCssFile(url){
    var link = document.createElement("link");
    link.href = url;
    link.type = "text/css";
    link.rel = "stylesheet";
    link.media = "screen,print";
    document.getElementsByTagName("head")[0].appendChild(link);
}

function htmlDecode(input){
    var e = document.createElement('div');
    e.innerHTML = input;
    return e.childNodes.length === 0 ? "" : e.childNodes[0].nodeValue;
}

function vote(thingId, direction){
    // determine if neutral vote
    if (direction == 1) {
        if ($("#"+thingId+" .upvote").css("color")=="rgb(255, 139, 96)") { // if already upvoted, neutralize.
            direction = 0;
        }
    } else { // downvote
        if ($("#"+thingId+" .downvote").css("color")=="rgb(148, 148, 255)") {
            direction = 0;
        }
    }

    Reddinator.vote(thingId, direction);
}

function voteCallback(thingId, direction){
    var upvote = $("#"+thingId).children(".vote").children(".upvote");
    var downvote = $("#"+thingId).children(".vote").children(".downvote");
    switch(direction){
        case "-1":
            upvote.css("color", color_vote);
            downvote.css("color", color_downvote_active);
            break;
        case "0":
            upvote.css("color", color_vote);
            downvote.css("color", color_vote);
            break;
        case "1":
            upvote.css("color", color_upvote_active);
            downvote.css("color", color_vote);
            break;
    }
    console.log("vote callback received: "+direction);
}

function comment(parentId, text){
    if (text==""){
        alert("Enter some text for the comment.");
        commentCallback(parentId, false);
        return;
    }
    //console.log(parentId+" "+text);
    Reddinator.comment(parentId, text);
}

function deleteComment(thingId){
    var answer = confirm("Are you sure you want to delete this comment?");
    if (answer){
        Reddinator.delete(thingId);
    }
}

function deleteCallback(thingId){
    $("#"+thingId).remove();
}