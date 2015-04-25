// set article from document hash
var articleId = document.location.hash.substring(1);
var username;

function init(themeColors, user){
    username = user;
    setTheme(themeColors);
}

function setTheme(themeColors){
    var themeColors = themeColors.split(",");
    $("body").css("background-color", themeColors[5]);
    $("body").css("color", themeColors[0]);
    $(".comment_user").css("color", themeColors[3]);
    $(".comment_score").css("color", themeColors[4]);
    $(".comment_reply_count").css("color", themeColors[4]);
    $("button").css("background-color", themeColors[3]);
    $("body").show();
}

function populateComments(json){
    var data = JSON.parse(json);
    $("#loading_view").hide();
    $("#base").show();
    recursivePopulate(data);
}

function recursivePopulate(data){
    for (var i in data){
        if (data[i].kind!="more"){
            appendComment(data[i].data.parent_id, data[i].data, false);
            if (data[i].data.replies!="" && data[i].data.replies.data.children!=null)
                recursivePopulate(data[i].data.replies.data.children);
        } else {
            appendMoreButton(data[i].data.parent_id, data[i].data);
        }
    }
}

function clearComments(){
    $("#base").html();
}

function showLoadingView(text){
    var loading = $("#loading_view");
    loading.children("h4").text(text);
    $("#base").hide();
    loading.show();
}
// java bind functions
function reloadComments(sort){
    showLoadingView("Loading...");
    $("#base").html('');
    Reddinator.reloadComments($("#sort_select").val());
}

function loadChildComments(moreId, children){
    Reddinator.loadChildren(moreId, children);
}

function vote(thingId, direction){
    // determine if neutral vote
    if (direction == 1) {
        if ($("#"+thingId+" .comment_upvote").attr("src")=="upvote_active.png") { // if already upvoted, neutralize.
            direction = 0;
        }
    } else { // downvote
        if ($("#"+thingId+" .comment_downvote").attr("src")=="downvote_active.png") {
            direction = 0;
        }
    }

    Reddinator.vote(thingId, direction);
}

function voteCallback(thingId, direction){
    var upvote = $("#"+thingId).children(".comment_vote").children(".comment_upvote");
    var downvote = $("#"+thingId).children(".comment_vote").children(".comment_downvote");
    switch(direction){
        case "-1":
            upvote.attr("src", "upvote.png");
            downvote.attr("src", "downvote_active.png");
            break;
        case "0":
            upvote.attr("src", "upvote.png");
            downvote.attr("src", "downvote.png");
            break;
        case "1":
            upvote.attr("src", "upvote_active.png");
            downvote.attr("src", "downvote.png");
            break;
    }
    console.log("vote callback received: "+thingId);
}

function comment(parentId, text){
    if (text==""){
        alert("Enter some text for the comment.");
        commentCallback(parentId, false);
        return;
    }
    console.log(parentId+" "+text);
    Reddinator.comment(parentId, text);
}

function commentCallback(parentId, commentData){
    console.log("comment callback called");
    var postElem;
    if (parentId.indexOf("t3_")!==-1){
        postElem = $("#post_comment");
    } else {
        postElem = $("#"+parentId+" > .post_box");
    }
    if (commentData){
        commentData = JSON.parse(commentData);
        postElem.children("textarea").val("");
        if (parentId.indexOf("t3_")===-1)
            postElem.hide();
        appendComment(parentId, commentData, true)
    }
    postElem.children("button").prop("disabled", false);
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

function startEdit(thingId){
    // skip if current is being edited
    var post_box = $("#"+thingId+" > .comment_text");
    if (!post_box.hasClass("editing")){
        // store html comment text
        post_box.data('comment_html', post_box.html());
        // prepare edit element
        var editElem = $("#edit_template").clone().show();
        editElem.find('textarea').val(post_box.text());
        // remove current html and append edit box
        post_box.html('');
        editElem.children().appendTo(post_box);
        post_box.addClass('editing');
    }
}
function cancelEdit(thingId){
    // skip if not being edited
    var post_box = $("#"+thingId+" > .comment_text");
    if (post_box.hasClass("editing")){
        // remove edit box and restore html content
        post_box.empty().html(post_box.data('comment_html'));
        post_box.removeClass('editing');
    }
}
function edit(thingId, text){
    if (text==""){
        alert("Enter some text for the comment.");
        editCallback(thingId, false);
        return;
    }
    Reddinator.edit(thingId, text);
}
function editCallback(thingId, commentData){
    // skip if not being edited or result false
    var post_box = $("#"+thingId+" > .comment_text");
    if (commentData && post_box.hasClass("editing")){
        commentData = JSON.parse(commentData);
        post_box.empty().html(htmlDecode(commentData.body_html));
        post_box.removeClass('editing');
    } else {
        post_box.children("button, textarea").prop("disabled", false);
    }
}

function populateChildComments(moreId, json){
    var data = JSON.parse(json);
    $("#"+moreId).remove();
    for (var i in data){
        if (data[i].kind!="more"){
            appendComment(data[i].data.parent_id, data[i].data, false);
        } else {
            appendMoreButton(data[i].data.parent_id, data[i].data);
        }
    }
}

function appendMoreButton(parentId, moreData){
    var moreElem = $("#more_template").clone().show();
    moreElem.attr("id", moreData.name);
    moreElem.children("h5").text("Load "+moreData.count+" more");
    moreElem.one('click',
        {id: moreData.name, children: moreData.children},
        function(event){
            $(this).children("h5").text("Loading...");
            loadChildComments(event.data.id, event.data.children.join(","));
        }
    );
    if (parentId.indexOf("t3_")!==-1){
        moreElem.css("margin-right", "0").appendTo("#base");
    } else {
        moreElem.appendTo("#"+parentId+"-replies");
        var repliesElem = $("#"+parentId).children(".comment_info").children(".comment_reply_count");
        repliesElem.text(parseInt(repliesElem.text())+moreData.count);
    }
}

function appendComment(parentId, commentData, prepend){
    var commentElem = $("#comment_template").clone().show();

    commentElem.attr("id", commentData.name);
    commentElem.find(".comment_replies").attr("id", commentData.name+"-replies");
    commentElem.find(".comment_text").html(htmlDecode(commentData.body_html));
    commentElem.find(".comment_user").text('/u/'+commentData.author).attr('href', 'http://www.reddit.com/u/'+commentData.author+'.compact');
    commentElem.find(".comment_score").text(commentData.score_hidden?'hidden':commentData.score);
    commentElem.find(".comment_reply_count").text("0");
    // check if likes
    if (commentData.hasOwnProperty('likes')){
        if (commentData.likes==1){
            commentElem.find(".comment_upvote").attr("src", "upvote_active.png");
        } else if (commentData.likes==-1) {
            commentElem.find(".comment_downvote").attr("src", "downvote_active.png");
        }
    }
    // check if author
    if (commentData.author==username){
        commentElem.find(".user_option").show();
    }
    if (parentId.indexOf("t3_")!==-1){
        if (prepend){
            commentElem.prependTo("#base");
        } else {
            commentElem.appendTo("#base");
        }
    } else {
        if (prepend){
            commentElem.prependTo("#"+parentId+"-replies");
        } else {
            commentElem.appendTo("#"+parentId+"-replies");
        }
        var parent = $("#"+parentId);
        parent.children('.reply_expand').show();
        var repliesElem = parent.children(".comment_info").children(".comment_reply_count");
        repliesElem.text(parseInt(repliesElem.text())+1);
    }
}

function htmlDecode(input){
    var e = document.createElement('div');
    e.innerHTML = input;
    return e.childNodes.length === 0 ? "" : e.childNodes[0].nodeValue;
}

function toggleReplies(element){
    var replies = $(element).parent().find(".comment_replies");
    if (replies.is(":visible")){
        replies.hide();
        $(element).children('h5').text("+");
        $(element).children('h6').text("show replies");
    } else {
        replies.show();
        $(element).children('h5').text("-");
        $(element).children('h6').text("hide replies");
    }
}

$(function(){
    // Layout testing code
    //$("#comment_template").clone().show().attr("id", 'test').appendTo("#base");
    //$("#comment_template").clone().show().attr("id", 'test1').appendTo("#test .comment_replies");
    $(document).on('click', ".comment_upvote", function(){
        vote($(this).parent().parent().attr("id"), 1);
    });
    $(document).on('click', ".comment_downvote", function(){
        vote($(this).parent().parent().attr("id"), -1);
    });
    $(document).on('click', ".post_toggle", function(){
        var elem = $(this).parent().parent().children(".post_reply");
        if (elem.is(":visible")){
            elem.hide();
        } else {
            $('.post_reply').hide();
            elem.show();
        }
    });
});