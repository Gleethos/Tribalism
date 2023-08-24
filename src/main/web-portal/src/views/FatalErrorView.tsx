import React from 'react';
import Button from '../components/atoms/Button';

function FatalErrorView(vm: any, errorEvents: any[]) {
    /*
        - A big heading saying "FATAL ERROR!"
        - A nicely formatted list of errors
        - A button to reload the page
    */
    return (
        <div className='App relative'>
            <header>
                <title>ERROR!</title>
            </header>
            <div style={{position: 'absolute', left: '50%', top: '50%', transform: 'translate(-50%, -50%)'}}>
                <h1>ERROR!</h1>
                <div style={{height: '20px'}}>
                    <p>
                        <span style={{color: 'red'}}>
                            Failed to bind to the start page of the MVVM server!
                        </span>
                        <br/>
                        Check the error log below for more information!
                    </p>
                </div>
                <div style={{height: '20px'}}>{vm}</div>
                <div style={{height: '200px', overflow: 'scroll', border: '1px solid black'}}>
                    <ul>
                        {errorEvents.map((error) => <li>{error.toString()}</li>)}
                    </ul>
                </div>
                <div style={{height: '20px'}}></div>
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