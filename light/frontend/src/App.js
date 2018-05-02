import React, {Component} from 'react';
import EventBus from 'vertx3-eventbus-client';
import './App.css';

class App extends Component {
    constructor(props) {
        super(props)

        this.state = {
            query: '',
            searchId: 0,
	    searchResults : []
        }

        const busOptions = {
            vertxbus_reconnect_attempts_max: Infinity, // Max reconnect attempts
            vertxbus_reconnect_delay_min: 1000, // Initial delay (in ms) before first reconnect attempt
            vertxbus_reconnect_delay_max: 5000, // Max delay (in ms) between reconnect attempts
            vertxbus_reconnect_exponent: 2, // Exponential backoff factor
            vertxbus_randomization_factor: 0.5 // Randomization factor between 0 and 1
        };
        this.eb = new EventBus('http://localhost:9191/bus', busOptions);
        this.eb.enableReconnect(true);
        this.eb.onopen = this.onEventBusOpen.bind(this);

    }

    newSearchId() {
        return this.state.searchId + 1;
    }

    onEventBusOpen() {
        console.log('onEventBusOpen');
        this.eb.registerHandler('search', this.onSearchResponse.bind(this));
    }

    onSearchResponse(error, message) {
        console.log('onSearchResponse(error, message=' + JSON.stringify(message) + ')');
        const searchResult = {
            title : message.body.query
	} 
        this.setState({'searchResults' : this.state.searchResults.concat([searchResult]) });
    }

    onQueryChanged(event) {
        this.setState({'query' : event.target.value });
    }

    onSearchButton() {
        console.log('onSearchButton - sending:' + this.state.query);
        this.eb.send('search', { query: this.state.query, 'searchId': this.state.searchId});
        this.setState({'searchId' : this.newSearchId()});
    }

    render() {
        return (
            <div className="App">
                <h1>FrostWire Light</h1>
                <hr/>

                <input onChange={(e) => this.onQueryChanged(e)} type="text" defaultValue="" placeholder="What are you looking for?" id="message"/>
                <input type="button" value="Search" onClick={this.onSearchButton.bind(this)}/>
                <br/>

                <SearchResultList searchResults={this.state.searchResults} />	        

                Status: {this.state.message}
            </div>
        );
    }
}

class SearchResultList extends Component {
    render() {
        return (
        <ul>
	{this.props.searchResults.map(function (searchResult, i) {
	       return <li key={i}>{searchResult.title}</li>
	})}
        </ul>);
    }
}

export default App;
