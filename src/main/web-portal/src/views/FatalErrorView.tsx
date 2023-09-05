import React from 'react';
import Button from '../components/atoms/Button';

function FatalErrorView(props: {data: {vm: string, errorEvents: any[]}}) {
    const {vm, errorEvents} = props.data;
    /*
        - A big heading saying "FATAL ERROR!"
        - A nicely formatted list of errors
        - A button to reload the page
    */
    // First we do some input validation, we log the types of the input
    console.log('FatalErrorView: vm: ' + typeof vm);
    console.log('FatalErrorView: errorEvents: ' + typeof errorEvents);
    return (
        <div className='App relative'>
            <header>
                <title>ERROR!</title>
            </header>
            <div style={{height: '100vh'}} className='flex items-center justify-center flex-col gap-6'>
                <h1>ERROR!</h1>
                <div>{vm}</div>
                <div style={{height: '20px'}}>
                    <p>
                        <span style={{color: 'red'}}>
                            Failed to bind to the {vm} page of the MVVM server!
                        </span>
                        <br/>
                        {errorEvents.length > 0 ? 'Check the error log below for more information!' : ''}
                    </p>
                </div>
                <div style={{overflow: 'scroll', border: '1px solid black', borderRadius: '8px', backgroundColor: 'salmon'}}>
                    <ul>
                        {errorEvents.map((error) => <li>{error.toString()}</li>)}
                    </ul>
                </div>
                <Button
                    onClick={() => window.location.reload()}
                    buttonStyles='flex gap-3 mt-6 items-center justify-center'
                    kind='primary'
                >
                    <span style={{color: 'red'}}>
                        Reload
                    </span>
                </Button>
            </div>
        </div>
    );
}

export default FatalErrorView;